import { spawn } from 'node:child_process'
import { createHash, randomUUID } from 'node:crypto'
import { existsSync } from 'node:fs'
import { rename, unlink, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { inspectArchive, prepareArchive, requirePassingK3dE2EVerification } from '../e2e/archive-images.js'
import { restorePreviousImages } from './rollback-k3d.mjs'
import { assertAssetsDatabaseIdentity, prepareAssetsBootstrapJob } from './assets-bootstrap-job.mjs'
import { prepareSchemaReset } from './schema-reset.mjs'
import { assertMarketDatabaseIdentity, prepareMarketBootstrapJob } from './market-bootstrap-job.mjs'
import { assertCoreDatabaseIdentity, prepareCoreBootstrapJob, assertNoActiveExpeditions } from './core-bootstrap-job.mjs'

const projectDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const kubeconfig = process.env.HERO_ASSOCIATION_K3D_KUBECONFIG || path.join(projectDirectory, 'deploy/k3d/.kubeconfig')
const cachedK3d = path.join(projectDirectory, 'deploy/k3d/.tools/k3d')
const k3d = process.env.K3D_BIN || (existsSync(cachedK3d) ? cachedK3d : 'k3d')
const namespace = 'hero-association'
const components = ['core', 'bff', 'expedition', 'market', 'assets', 'frontend']
const kubectl = ['--kubeconfig', kubeconfig]
const clusterEnvironment = { ...process.env, KUBECONFIG: kubeconfig }
const hpaManifest = path.join(projectDirectory, 'deploy/k8s/backend/hpa.yaml')

function commandString(command, args) {
  return [command, ...args].join(' ')
}

function run(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      env: clusterEnvironment,
      stdio: [options.input === undefined ? 'ignore' : 'pipe', options.capture ? 'pipe' : 'inherit', 'inherit'],
      cwd: options.cwd || projectDirectory,
    })
    if (options.input !== undefined) child.stdin.end(options.input)
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

async function resetCoreDatabaseFromArchive(archive, onResetStarted) {
  const jobs = []
  const definitions = [
    { name: 'core', database: 'hero_association', prepare: prepareCoreBootstrapJob, assert: assertCoreDatabaseIdentity },
    { name: 'assets', database: 'hero_association_assets', prepare: prepareAssetsBootstrapJob, assert: assertAssetsDatabaseIdentity },
    { name: 'market', database: 'hero_association_market', prepare: prepareMarketBootstrapJob, assert: assertMarketDatabaseIdentity },
  ]
  // Validate all targets before stopping any writer or resetting any data.
  for (const entry of definitions) {
    const identity = await run('kubectl', [
      ...kubectl, '-n', namespace, 'exec', 'deployment/postgres-' + entry.name, '-c', 'postgres', '--',
      'psql', '-U', entry.database, '-d', entry.database, '-At', '-c', 'SELECT current_database(), current_user',
    ], { capture: true })
    entry.assert(identity)
    entry.resetSql = prepareSchemaReset(entry.database, identity)
    const template = JSON.parse(await run('kubectl', [...kubectl, 'create', '-f',
      path.join(projectDirectory, 'deploy/k8s/backend/' + entry.name + '-db-bootstrap.yaml'),
      '--dry-run=client', '-o', 'json'], { capture: true }))
    jobs.push({ name: entry.name, job: entry.prepare(template, archive.images[entry.name].ref) })
  }
  const previousReplicas = {}
  for (const component of components) previousReplicas[component] = (await getDeployment(component)).deployment.spec.replicas ?? 0
  try {
    await run('kubectl', [...kubectl, '-n', namespace, 'delete', 'hpa/core', 'hpa/bff', '--ignore-not-found=true'])
    // Stop admission and browser traffic first, then drain the three asset writers.
    for (const component of ['bff', 'expedition', 'frontend', 'market', 'core', 'assets']) {
      await run('kubectl', [...kubectl, '-n', namespace, 'scale', 'deployment/' + component, '--replicas=0'])
      await run('kubectl', [...kubectl, '-n', namespace, 'wait', '--for=delete', 'pod', '-l', 'app=' + component, '--timeout=5m'])
    }
    const unfinished = await run('kubectl', [...kubectl, '-n', namespace, 'exec',
      'deployment/postgres-core', '-c', 'postgres', '--', 'psql', '-U', 'hero_association',
      '-d', 'hero_association', '-At', '-c',
      'SELECT count(*) FROM expedition_reservation WHERE appliedat IS NULL AND releasedat IS NULL'], { capture: true })
    const activeRuns = await run('kubectl', [...kubectl, '-n', namespace, 'exec',
      'statefulset/redis-expedition', '-c', 'redis', '--', 'redis-cli', '--scan',
      '--pattern', 'ha:expedition:v1:*:active'], { capture: true })
    assertNoActiveExpeditions(unfinished, activeRuns)
    const corePending = await run('kubectl', [...kubectl, '-n', namespace, 'exec',
      'deployment/postgres-core', '-c', 'postgres', '--', 'psql', '-U', 'hero_association',
      '-d', 'hero_association', '-At', '-c',
      "DO $$ BEGIN IF to_regclass('public.asset_workflow') IS NOT NULL THEN IF EXISTS (SELECT 1 FROM asset_workflow WHERE status IN ('PENDING','CONFLICT')) THEN RAISE EXCEPTION 'Unfinished Core asset workflows'; END IF; END IF; END $$;"], { capture: true })
    const marketPending = await run('kubectl', [...kubectl, '-n', namespace, 'exec',
      'deployment/postgres-market', '-c', 'postgres', '--', 'psql', '-U', 'hero_association_market',
      '-d', 'hero_association_market', '-At', '-c',
      "SELECT count(*) FROM market_placement WHERE state IN ('PENDING_RESERVATION','PENDING_ABORT','CONFLICT')"], { capture: true })
    const marketRecoveries = await run('kubectl', [...kubectl, '-n', namespace, 'exec',
      'deployment/postgres-market', '-c', 'postgres', '--', 'psql', '-U', 'hero_association_market',
      '-d', 'hero_association_market', '-At', '-c',
      "SELECT count(*) FROM market_order WHERE state IN ('PENDING_CANCEL','CONFLICT') OR EXISTS (SELECT 1 FROM market_trade WHERE state IN ('PENDING_SETTLEMENT','CONFLICT'))"], { capture: true })
    if (corePending !== 'DO' || marketPending !== '0' || marketRecoveries !== '0') {
      throw new Error('Finish Core and Market asset operations before resetting the coupled lab databases')
    }
  } catch (error) {
    for (const component of components) await run('kubectl', [...kubectl, '-n', namespace, 'scale', 'deployment/' + component, '--replicas=' + previousReplicas[component]])
    await run('kubectl', [...kubectl, 'apply', '-f', hpaManifest])
    throw error
  }
  console.log('Resetting the coupled Core, Assets and Market lab databases from the verified archive')
  onResetStarted()
  await run('kubectl', [...kubectl, '-n', namespace, 'delete', 'hpa/core', '--ignore-not-found=true'])
  const coreAssetsEnvironment = { spec: { template: { spec: { containers: [{ name: 'core', env: [
    { name: 'HERO_ASSOCIATION_ASSETS_BASE_URL', value: 'http://assets:8085' },
    { name: 'HERO_ASSOCIATION_ASSETS_CORE_SERVICE_KEY', valueFrom: { secretKeyRef: {
      name: 'hero-association-assets-credentials', key: 'HERO_ASSOCIATION_ASSETS_CORE_SERVICE_KEY',
    } } },
    { name: 'HERO_ASSOCIATION_ASSETS_MARKET_SERVICE_KEY', $patch: 'delete' },
  ] }] } } } }
  await run('kubectl', [...kubectl, '-n', namespace, 'patch', 'deployment/core', '--type=strategic', '-p', JSON.stringify(coreAssetsEnvironment)])
  await run('kubectl', [...kubectl, '-n', namespace, 'set', 'env', 'deployment/market', 'HERO_ASSOCIATION_ASSETS_BASE_URL=http://assets:8085', 'HERO_ASSOCIATION_CORE_BASE_URL-'])
  await run('kubectl', [...kubectl, '-n', namespace, 'set', 'env', 'deployment/bff', 'HERO_ASSOCIATION_ASSETS_BASE_URL=http://assets:8085'])
  await run('kubectl', [...kubectl, 'apply', '-f', path.join(projectDirectory, 'deploy/k8s/mesh/assets-authorization.yaml')])
  await run('kubectl', [...kubectl, 'apply', '-f', path.join(projectDirectory, 'deploy/k8s/mesh/core-market-authorization.yaml')])
  await run('kubectl', [...kubectl, '-n', namespace, 'delete', 'authorizationpolicy/core-from-market', '--ignore-not-found=true'])
  // Hibernate drops only mapped tables; explicitly remove retired Core asset tables too.
  for (const entry of definitions) {
    await run('kubectl', [...kubectl, '-n', namespace, 'exec', 'deployment/postgres-' + entry.name,
      '-c', 'postgres', '--', 'psql', '-U', entry.database, '-d', entry.database,
      '--set', 'ON_ERROR_STOP=1', '--single-transaction', '-c', entry.resetSql])
  }
  const completed = {}
  for (const entry of jobs) {
    const created = JSON.parse(await run('kubectl', [...kubectl, '-n', namespace, 'create', '-f', '-', '-o', 'json'],
      { capture: true, input: JSON.stringify(entry.job) }))
    const jobName = created.metadata?.name
    if (!new RegExp('^' + entry.name + '-db-bootstrap-[a-z0-9]+$').test(jobName ?? '')) throw new Error('Unexpected bootstrap Job name')
    try {
      await run('kubectl', [...kubectl, '-n', namespace, 'wait', '--for=condition=complete', 'job/' + jobName, '--timeout=5m'])
    } catch (error) {
      await run('kubectl', [...kubectl, '-n', namespace, 'logs', 'job/' + jobName, '--all-containers=true']).catch(() => {})
      throw new Error('Coupled database reset failed; Core, Assets and Market remain stopped', { cause: error })
    }
    completed[entry.name] = jobName
  }
  for (const component of components) await run('kubectl', [...kubectl, '-n', namespace, 'set', 'image', 'deployment/' + component, component + '=' + archive.images[component].ref])
  for (const component of ['assets', 'core', 'market', 'expedition', 'bff', 'frontend']) {
    await run('kubectl', [...kubectl, '-n', namespace, 'scale', 'deployment/' + component, '--replicas=' + previousReplicas[component]])
    await run('kubectl', [...kubectl, '-n', namespace, 'rollout', 'status', 'deployment/' + component, '--timeout=5m'])
  }
  await run('kubectl', [...kubectl, 'apply', '-f', hpaManifest])
  return completed
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

async function verifyRunningPodImages(archive, selectedComponents = components) {
  const nodes = JSON.parse(await run('kubectl', [...kubectl, 'get', 'nodes', '-o', 'json'], { capture: true }))
  const platforms = new Map(nodes.items.map((node) => [
    node.metadata.name,
    `${node.status.nodeInfo.operatingSystem}/${node.status.nodeInfo.architecture}`,
  ]))
  const expected = new Map()
  const observed = {}
  for (const component of selectedComponents) {
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
async function verifyRunningPodImagesSettled(archive, selectedComponents = components) {
  for (let attempt = 1; attempt <= 12; attempt++) {
    try {
      return await verifyRunningPodImages(archive, selectedComponents)
    } catch (error) {
      if (attempt === 12) throw error
      console.log("Pod image check is not settled (attempt " + attempt + "): " + error.message)
      await new Promise((resolve) => setTimeout(resolve, 5000))
    }
  }
}

async function verifyDeploymentImageReferences(archive, selectedComponents = components) {
  for (const component of selectedComponents) {
    const { image } = await getDeployment(component)
    if (image !== archive.images[component].ref) {
      throw new Error('Deployment ' + component + ' does not reference the archived image')
    }
  }
}

async function recordPromotion(archive, observedPods, coreBootstrapJob) {
  const record = {
    version: 1,
    result: 'passed',
    cluster: 'k3d-hero-association',
    namespace,
    buildId: archive.buildId,
    promoteComponent: archive.promoteComponent || 'all',
    archiveSha256: archive.archiveSha256,
    coreDatabaseReset: coreBootstrapJob !== null,
    marketDatabaseReset: coreBootstrapJob !== null,
    assetsDatabaseReset: coreBootstrapJob !== null,
    assetsBootstrapJob: coreBootstrapJob?.assets ?? null,
    coreBootstrapJob: coreBootstrapJob?.core ?? null,
    marketBootstrapJob: coreBootstrapJob?.market ?? null,
    images: Object.fromEntries(components.map((component) => [component, {
      ref: archive.images[component].ref,
      id: archive.images[component].id,
      pods: observedPods[component],
    }])),
    checks: { archiveK3dE2E: 'passed', podImages: 'passed', browserE2E: 'passed', expeditionApiE2E: 'passed', mapE2E: 'passed', marketK6: 'passed' },
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
  const mode = process.argv[2] === '--verify-only' ? 'verify-only'
    : process.argv[2] === '--verify-baseline' ? 'verify-baseline'
      : ['--reset-core-db', '--reset-game-db'].includes(process.argv[2]) ? 'reset-core-db' : 'promote'
  if (process.argv.length !== (mode === 'promote' ? 3 : 4)) {
    throw new Error('Usage: node deploy-k3d.mjs [--verify-only|--verify-baseline|--reset-game-db] artifacts/<build-id>/all')
  }
  if (!existsSync(kubeconfig)) {
    throw new Error('Missing isolated k3d kubeconfig: ' + kubeconfig)
  }
  const context = await run('kubectl', [...kubectl, 'config', 'current-context'], { capture: true })
  if (context !== 'k3d-hero-association') {
    throw new Error('Refusing to use Kubernetes context ' + context)
  }

  await run('bash', [path.join(projectDirectory, 'deploy/k3d/require-full-k3d.sh')])

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

  const archive = await inspectArchive(process.argv[mode === 'promote' ? 2 : 3])
  if (mode === 'reset-core-db' && archive.promoteComponent) {
    throw new Error('A coupled database reset requires a complete six-service archive')
  }
  if (archive.promoteComponent) {
    const baselineComponents = components.filter((component) => component !== archive.promoteComponent)
    await verifyDeploymentImageReferences(archive, baselineComponents)
    await verifyRunningPodImages(archive, baselineComponents)
    console.log('Verified unchanged k3d service images against the archive')
  }
  if (mode === 'verify-baseline') {
    if (!archive.promoteComponent) throw new Error('Baseline verification requires a service promotion archive')
    return
  }
  await requirePassingK3dE2EVerification(archive)
  if (mode === 'verify-only') {
    await verifyDeploymentImageReferences(archive)
    await verifyRunningPodImages(archive)
    console.log('k3d deployment matches E2E-verified archive ' + archive.buildId)
    return
  }
  await prepareArchive(archive.archiveDirectory)
  console.log('Promoting E2E-verified archive ' + archive.buildId + ' (' + archive.archiveSha256 + ')')
  const promotedComponents = archive.promoteComponent ? [archive.promoteComponent] : components
  await run(k3d, ['image', 'import', ...promotedComponents.map((component) => archive.images[component].ref), '--cluster', 'hero-association'])

  const targetImages = Object.fromEntries(components.map((component) => [component, archive.images[component].ref]))
  const changed = []
  let coreBootstrapJob = null
  let resetStarted = false
  try {
    if (mode === 'reset-core-db') {
      coreBootstrapJob = await resetCoreDatabaseFromArchive(archive, () => { resetStarted = true })
    }
    for (const component of promotedComponents) {
      if (mode === 'reset-core-db') continue
      const target = archive.images[component].ref
      if (previous[component] !== target) {
        changed.push(component)
        await setImage(component, target)
      }
    }
    await verifyDeploymentImageReferences(archive)
    await verifyRunningPodImages(archive)
    await run('npm', ['run', 'test:k3d'], { cwd: path.join(projectDirectory, 'e2e') })
    await run('npm', ['run', 'test:k3d:expedition'], { cwd: path.join(projectDirectory, 'e2e') })
    await run('npm', ['run', 'test:k3d:map'], { cwd: path.join(projectDirectory, 'e2e') })
    await run('npm', ['run', 'test:market:k6'], { cwd: path.join(projectDirectory, 'e2e') })
    for (const component of promotedComponents) {
      await run('kubectl', [...kubectl, '-n', namespace, 'rollout', 'status', 'deployment/' + component, '--timeout=5m'])
    }
    const observedPods = await verifyRunningPodImagesSettled(archive)
    await recordPromotion(archive, observedPods, coreBootstrapJob)
  } catch (error) {
    console.error('Promotion failed: ' + error.message)
    if (resetStarted) {
      throw new Error('Core, Assets and Market reset began; automatic image rollback is unsafe. Inspect the k3d deployments and bootstrap Job before retrying.', { cause: error })
    }
    const rollbackErrors = await restorePreviousImages(
      changed, previous, targetImages,
      async (component) => (await getDeployment(component)).image,
      setImage,
    )
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
