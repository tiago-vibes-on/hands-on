import { spawn } from 'node:child_process'
import { createHash, randomUUID } from 'node:crypto'
import { existsSync } from 'node:fs'
import { rename, unlink, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { inspectArchive, prepareArchive, requirePassingE2EVerification } from '../e2e/archive-images.js'

const projectDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const kubeconfig = path.join(projectDirectory, 'deploy/k3d/.kubeconfig')
const cachedK3d = path.join(projectDirectory, 'deploy/k3d/.tools/k3d')
const k3d = process.env.K3D_BIN || (existsSync(cachedK3d) ? cachedK3d : 'k3d')
const namespace = 'hero-association'
const components = ['core', 'bff', 'frontend']
const kubectl = ['--kubeconfig', kubeconfig]
const clusterEnvironment = { ...process.env, KUBECONFIG: kubeconfig }

function commandString(command, args) {
  return [command, ...args].join(' ')
}

function run(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      env: clusterEnvironment,
      stdio: options.capture ? ['ignore', 'pipe', 'inherit'] : 'inherit',
      cwd: options.cwd || projectDirectory,
    })
    let output = ''
    if (options.capture) {
      child.stdout.setEncoding('utf8')
      child.stdout.on('data', (chunk) => { output += chunk })
    }
    child.once('error', reject)
    child.once('close', (code, signal) => {
      if (code === 0) {
        resolve(options.raw ? output : output.trim())
      } else {
        reject(new Error(commandString(command, args) + ' failed (' + (signal || code) + ')'))
      }
    })
  })
}

async function getDeployment(component) {
  const output = await run('kubectl', [...kubectl, '-n', namespace, 'get', 'deployment/' + component, '-o', 'json'], { capture: true })
  const deployment = JSON.parse(output)
  const container = deployment.spec.template.spec.containers.find((entry) => entry.name === component)
  if (!container || container.imagePullPolicy !== 'Never') {
    throw new Error('Deployment ' + component + ' must have its named container and imagePullPolicy Never')
  }
  return { deployment, image: container.image }
}

async function setImage(component, image) {
  await run('kubectl', [...kubectl, '-n', namespace, 'set', 'image', 'deployment/' + component, component + '=' + image])
  await run('kubectl', [...kubectl, '-n', namespace, 'rollout', 'status', 'deployment/' + component, '--timeout=5m'])
}

async function readArchiveBlob(archive, digest) {
  if (!/^sha256:[a-f0-9]{64}$/.test(digest)) throw new Error('Invalid OCI blob digest: ' + digest)
  const blob = await run('tar', [
    '-xOf', path.join(archive.archiveDirectory, 'images.tar'),
    'blobs/sha256/' + digest.slice(7),
  ], { capture: true, raw: true })
  if (createHash('sha256').update(blob).digest('hex') !== digest.slice(7)) {
    throw new Error('OCI archive blob does not match its digest: ' + digest)
  }
  return JSON.parse(blob)
}

async function expectedImageDigests(archive, component, platform) {
  const indexDigest = archive.images[component].id
  const index = await readArchiveBlob(archive, indexDigest)
  const [os, architecture] = platform.split('/')
  const matches = index.manifests?.filter((entry) =>
    entry.platform?.os === os && entry.platform?.architecture === architecture)
  if (matches?.length !== 1) {
    throw new Error(`Archive ${component} image has ${matches?.length ?? 0} manifests for ${platform}`)
  }
  const manifestDigest = matches[0].digest
  const manifest = await readArchiveBlob(archive, manifestDigest)
  const configDigest = manifest.config?.digest
  if (!/^sha256:[a-f0-9]{64}$/.test(configDigest)) {
    throw new Error(`Archive ${component} image has no valid ${platform} config digest`)
  }
  return new Set([indexDigest, manifestDigest, configDigest])
}

async function verifyRunningPodImages(archive) {
  const nodes = JSON.parse(await run('kubectl', [...kubectl, 'get', 'nodes', '-o', 'json'], { capture: true }))
  const platforms = new Map(nodes.items.map((node) => [
    node.metadata.name,
    `${node.status.nodeInfo.operatingSystem}/${node.status.nodeInfo.architecture}`,
  ]))
  const expected = new Map()
  const observed = {}
  for (const component of components) {
    const { deployment } = await getDeployment(component)
    const labels = deployment.spec.selector.matchLabels ?? {}
    if (!Object.keys(labels).length || deployment.spec.selector.matchExpressions?.length) {
      throw new Error(`Deployment ${component} has an unsupported Pod selector`)
    }
    const selector = Object.entries(labels).map(([name, value]) => `${name}=${value}`).join(',')
    const listed = JSON.parse(await run('kubectl', [
      ...kubectl, '-n', namespace, 'get', 'pods', '-l', selector, '-o', 'json',
    ], { capture: true }))
    const pods = listed.items.filter((pod) => !pod.metadata.deletionTimestamp)
    if (pods.length !== (deployment.spec.replicas ?? 1)) {
      throw new Error(`Deployment ${component} has ${pods.length} active Pods, not ${deployment.spec.replicas ?? 1}`)
    }
    observed[component] = []
    for (const pod of pods) {
      const platform = platforms.get(pod.spec.nodeName)
      if (!platform) throw new Error(`Unknown node platform for Pod ${pod.metadata.name}`)
      const key = `${component}/${platform}`
      if (!expected.has(key)) {
        expected.set(key, await expectedImageDigests(archive, component, platform))
      }
      const configured = pod.spec.containers.find((container) => container.name === component)
      const running = pod.status.containerStatuses?.find((container) => container.name === component)
      const imageDigest = running?.imageID?.match(/sha256:[a-f0-9]{64}$/)?.[0]
      if (configured?.image !== archive.images[component].ref || !running?.ready ||
          !expected.get(key).has(imageDigest)) {
        throw new Error(`Pod ${pod.metadata.name} is not running the verified ${component} archive image`)
      }
      observed[component].push({ pod: pod.metadata.name, node: pod.spec.nodeName, platform, imageId: imageDigest })
    }
    observed[component].sort((a, b) => a.pod.localeCompare(b.pod))
    console.log(`Verified ${pods.length} running ${component} Pod image ID(s) against the archive`)
  }
  return observed
}
async function verifyDeploymentImageReferences(archive) {
  for (const component of components) {
    const { image } = await getDeployment(component)
    if (image !== archive.images[component].ref) {
      throw new Error('Deployment ' + component + ' does not reference the archived image')
    }
  }
}

async function recordPromotion(archive, observedPods) {
  const record = {
    version: 1,
    result: 'passed',
    cluster: 'k3d-hero-association',
    namespace,
    buildId: archive.buildId,
    archiveSha256: archive.archiveSha256,
    images: Object.fromEntries(components.map((component) => [component, {
      ref: archive.images[component].ref,
      id: archive.images[component].id,
      pods: observedPods[component],
    }])),
    checks: { archiveE2E: 'passed', podImages: 'passed', browserE2E: 'passed', marketK6: 'passed' },
    recordedAt: new Date().toISOString(),
  }
  const destination = path.join(archive.archiveDirectory, 'k3d-promotion.json')
  const temporary = `${destination}.${randomUUID()}.tmp`
  try {
    await writeFile(temporary, `${JSON.stringify(record, null, 2)}\n`, { flag: 'wx' })
    await rename(temporary, destination)
  } finally {
    await unlink(temporary).catch((error) => {
      if (error.code !== 'ENOENT') throw error
    })
  }
  console.log('Saved k3d promotion result: ' + destination)
}

async function main() {
  const verifyOnly = process.argv[2] === '--verify-only'
  if (process.argv.length !== (verifyOnly ? 4 : 3)) {
    throw new Error('Usage: node deploy-k3d.mjs [--verify-only] artifacts/<build-id>/all')
  }
  if (!existsSync(kubeconfig)) {
    throw new Error('Missing isolated k3d kubeconfig: ' + kubeconfig)
  }
  const context = await run('kubectl', [...kubectl, 'config', 'current-context'], { capture: true })
  if (context !== 'k3d-hero-association') {
    throw new Error('Refusing to use Kubernetes context ' + context)
  }

  const previous = {}
  for (const component of components) {
    const { deployment, image } = await getDeployment(component)
    const replicas = deployment.spec.replicas || 1
    if (deployment.status.observedGeneration < deployment.metadata.generation ||
        deployment.status.readyReplicas !== replicas ||
        deployment.status.updatedReplicas !== replicas) {
      throw new Error('Deployment ' + component + ' is not healthy')
    }
    previous[component] = image
  }

  const archive = await inspectArchive(process.argv[verifyOnly ? 3 : 2])
  await requirePassingE2EVerification(archive)
  if (verifyOnly) {
    await verifyDeploymentImageReferences(archive)
    await verifyRunningPodImages(archive)
    console.log('k3d deployment matches E2E-verified archive ' + archive.buildId)
    return
  }
  await prepareArchive(archive.archiveDirectory)
  console.log('Promoting E2E-verified archive ' + archive.buildId + ' (' + archive.archiveSha256 + ')')
  await run(k3d, ['image', 'import', ...components.map((component) => archive.images[component].ref), '--cluster', 'hero-association'])

  const changed = []
  try {
    for (const component of components) {
      const target = archive.images[component].ref
      if (previous[component] !== target) {
        changed.push(component)
        await setImage(component, target)
      }
    }
    await verifyDeploymentImageReferences(archive)
    await verifyRunningPodImages(archive)
    await run('npm', ['run', 'test:k3d'], { cwd: path.join(projectDirectory, 'e2e') })
    await run('npm', ['run', 'test:market:k6'], { cwd: path.join(projectDirectory, 'e2e') })
    const observedPods = await verifyRunningPodImages(archive)
    await recordPromotion(archive, observedPods)
  } catch (error) {
    console.error('Promotion failed: ' + error.message)
    const rollbackErrors = []
    for (const component of changed.reverse()) {
      try {
        const { image } = await getDeployment(component)
        if (image === archive.images[component].ref) {
          await setImage(component, previous[component])
        } else if (image !== previous[component]) {
          throw new Error('image changed concurrently to ' + image)
        }
      } catch (rollbackError) {
        rollbackErrors.push(component + ': ' + rollbackError.message)
      }
    }
    if (rollbackErrors.length) {
      throw new Error('Promotion failed; rollback incomplete: ' + rollbackErrors.join('; '), { cause: error })
    }
    throw new Error('Promotion failed; previous deployment images restored', { cause: error })
  }
  console.log('k3d promotion verified: ' + components.map((component) => archive.images[component].ref).join(', '))
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
