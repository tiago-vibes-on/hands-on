import { execFile } from 'node:child_process'
import { createHash } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { promisify } from 'node:util'

import { inspectArchive, prepareArchive } from './archive-images.js'

const executeFile = promisify(execFile)
const components = ['core', 'bff', 'expedition', 'market', 'assets', 'world', 'quest', 'frontend']
const [action, metadataFile, archiveDirectory, kubeconfig, namespace] = process.argv.slice(2)
async function recordK3dVerification(archive, status) {
  if (archive.component !== 'all' || !['pending', 'passed'].includes(status)) {
    throw new Error('Isolated k3d verification requires a full archive and valid status')
  }
  const record = {
    version: 1,
    suite: 'k3d-isolated',
    result: status,
    buildId: archive.buildId,
    archiveSha256: archive.archiveSha256,
    imageIds: Object.fromEntries(
      components.map((component) => [component, archive.images[component].id]),
    ),
    recordedAt: new Date().toISOString(),
  }
  await writeFile(
    path.join(archive.archiveDirectory, 'k3d-e2e-verification.json'),
    `${JSON.stringify(record, null, 2)}\n`,
  )
}


async function readCandidate() {
  const saved = JSON.parse(await readFile(metadataFile, 'utf8'))
  const archive = await inspectArchive(saved.archiveDirectory)
  if (archive.component !== 'all' || archive.archiveSha256 !== saved.archiveSha256 ||
      components.some((component) => archive.images[component].id !== saved.images[component].id)) {
    throw new Error('Candidate metadata no longer matches the checked archive')
  }
  return archive
}

async function kubectl(...args) {
  const { stdout } = await executeFile('kubectl', ['--kubeconfig', kubeconfig, ...args], {
    maxBuffer: 10 * 1024 * 1024,
  })
  return stdout
}

async function readArchiveBlob(archive, digest) {
  if (!/^sha256:[a-f0-9]{64}$/.test(digest)) {
    throw new Error(`Invalid OCI blob digest: ${digest}`)
  }
  const { stdout } = await executeFile('tar', [
    '-xOf', path.join(archive.archiveDirectory, 'images.tar'),
    `blobs/sha256/${digest.slice(7)}`,
  ], { encoding: 'buffer', maxBuffer: 10 * 1024 * 1024 })
  if (createHash('sha256').update(stdout).digest('hex') !== digest.slice(7)) {
    throw new Error(`OCI archive blob checksum mismatch: ${digest}`)
  }
  return JSON.parse(stdout.toString('utf8'))
}

async function expectedPodDigests(archive, component, platform) {
  const indexDigest = archive.images[component].id
  const index = await readArchiveBlob(archive, indexDigest)
  const [os, architecture] = platform.split('/')
  const matching = index.manifests?.filter((manifest) =>
    manifest.platform?.os === os && manifest.platform?.architecture === architecture)
  if (matching?.length !== 1) {
    throw new Error(`Archive ${component} has no unique ${platform} manifest`)
  }
  const manifestDigest = matching[0].digest
  const manifest = await readArchiveBlob(archive, manifestDigest)
  const configDigest = manifest.config?.digest
  if (!/^sha256:[a-f0-9]{64}$/.test(configDigest)) {
    throw new Error(`Archive ${component} has no valid ${platform} config digest`)
  }
  return new Set([indexDigest, manifestDigest, configDigest])
}

async function verifyRunningPods(archive) {
  const nodes = JSON.parse(await kubectl('get', 'nodes', '-o', 'json'))
  const platforms = new Map(nodes.items.map((node) => [
    node.metadata.name,
    `${node.status.nodeInfo.operatingSystem}/${node.status.nodeInfo.architecture}`,
  ]))
  for (const component of components) {
    const deployment = JSON.parse(await kubectl('-n', namespace,
      'get', `deployment/${component}`, '-o', 'json'))
    const selector = deployment.spec.selector.matchLabels
    const expectedReplicas = ['bff', 'market', 'assets', 'world', 'quest'].includes(component) ? 2 : 1
    if (Object.keys(selector).length !== 1 || selector.app !== component ||
        deployment.spec.replicas !== expectedReplicas) {
      throw new Error(`Isolated ${component} Deployment has an unexpected selector or replica count`)
    }
    const pods = JSON.parse(await kubectl('-n', namespace,
      'get', 'pods', '-l', `app=${component}`, '-o', 'json'))
      .items.filter((pod) => !pod.metadata.deletionTimestamp)
    if (pods.length !== expectedReplicas) {
      throw new Error(`Expected ${expectedReplicas} isolated ${component} Pods`)
    }
    for (const pod of pods) {
      const platform = platforms.get(pod.spec.nodeName)
      if (!platform) throw new Error(`Unknown node platform for ${pod.metadata.name}`)
      const expected = await expectedPodDigests(archive, component, platform)
      const configured = pod.spec.containers.find((container) => container.name === component)
      const running = pod.status.containerStatuses?.find((container) => container.name === component)
      const podDigest = running?.imageID?.match(/sha256:[a-f0-9]{64}$/)?.[0]
      if (configured?.image !== archive.images[component].ref || !running?.ready ||
          !expected.has(podDigest)) {
        throw new Error(`Isolated ${component} Pod does not match its archived image`)
      }
      console.log(`Verified isolated ${component} Pod ${pod.metadata.name} image ID against archive`)
    }
  }
}

if (action === 'prepare') {
  if (!metadataFile || !archiveDirectory) {
    throw new Error('Usage: prepare METADATA_FILE ARCHIVE_DIRECTORY')
  }
  const archive = await prepareArchive(archiveDirectory)
  if (archive.component !== 'all') {
    throw new Error('Candidate E2E requires a complete eight-image archive')
  }
  await recordK3dVerification(archive, 'pending')
  await writeFile(metadataFile, `${JSON.stringify(archive)}\n`, { mode: 0o600 })
} else if (action === 'verify') {
  if (!metadataFile || !kubeconfig || !namespace) {
    throw new Error('Usage: verify METADATA_FILE - KUBECONFIG NAMESPACE')
  }
  await verifyRunningPods(await readCandidate())
} else if (action === 'pass') {
  if (!metadataFile) throw new Error('Usage: pass METADATA_FILE')
  await recordK3dVerification(await readCandidate(), 'passed')
} else {
  throw new Error('Usage: k3d-candidate-archive.mjs prepare|verify|pass ...')
}
