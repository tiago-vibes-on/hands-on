import { execFile } from 'node:child_process'
import { createHash } from 'node:crypto'
import { createReadStream, existsSync } from 'node:fs'
import { mkdir, mkdtemp, readFile, rename, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'

import { inspectArchive, prepareArchive } from '../e2e/archive-images.js'

const executeFile = promisify(execFile)
const projectDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const kubeconfig = process.env.HERO_ASSOCIATION_K3D_KUBECONFIG || path.join(projectDirectory, 'deploy/k3d/.kubeconfig')
const components = ['core', 'bff', 'frontend']

async function command(name, args) {
  const { stdout } = await executeFile(name, args, { maxBuffer: 10 * 1024 * 1024 })
  return stdout.trim()
}

async function checksum(file) {
  const hash = createHash('sha256')
  for await (const chunk of createReadStream(file)) hash.update(chunk)
  return hash.digest('hex')
}

function manifestValue(lines, name) {
  const matches = lines.filter((line) => line.startsWith(`${name}=`))
  if (matches.length !== 1) throw new Error(`Candidate manifest needs one ${name}`)
  return matches[0].slice(name.length + 1)
}

async function liveImage(component) {
  const raw = await command('kubectl', [
    '--kubeconfig', kubeconfig, '-n', 'hero-association', 'get', `deployment/${component}`, '-o', 'json',
  ])
  const deployment = JSON.parse(raw)
  const replicas = deployment.spec.replicas || 1
  if (deployment.status.observedGeneration < deployment.metadata.generation ||
      deployment.status.readyReplicas !== replicas || deployment.status.updatedReplicas !== replicas) {
    throw new Error(`Baseline deployment ${component} is not healthy`)
  }
  const container = deployment.spec.template.spec.containers.find((entry) => entry.name === component)
  const ref = container?.image
  if (container?.imagePullPolicy !== 'Never' ||
      !new RegExp(`^hero-association-${component}:[A-Za-z0-9_.-]+$`).test(ref)) {
    throw new Error(`Baseline deployment ${component} has an invalid local image`)
  }
  let id
  try {
    id = await command('docker', ['image', 'inspect', '--format', '{{.Id}}', ref])
  } catch {
    throw new Error(`Baseline image ${ref} is missing from local Docker; restore its verified archive`)
  }
  if (!/^sha256:[a-f0-9]{64}$/.test(id)) throw new Error(`Invalid Docker image ID for ${ref}`)
  return { ref, id }
}

async function main() {
  const [component, candidateDirectory, destination] = process.argv.slice(2)
  if (process.argv.length !== 5 || !components.includes(component)) {
    throw new Error('Usage: node assemble-service-archive.mjs <core|bff|frontend> <candidate-archive> <full-archive>')
  }
  const candidate = await inspectArchive(candidateDirectory)
  if (candidate.component !== component || candidate.promoteComponent) {
    throw new Error(`Expected a one-image ${component} candidate archive`)
  }
  if (existsSync(destination)) throw new Error(`Artifact already exists: ${destination}`)
  const context = await command('kubectl', ['--kubeconfig', kubeconfig, 'config', 'current-context'])
  if (context !== 'k3d-hero-association') throw new Error(`Refusing Kubernetes context ${context}`)

  await prepareArchive(candidateDirectory)
  const images = {}
  for (const imageComponent of components) {
    images[imageComponent] = imageComponent === component
      ? candidate.images[component] : await liveImage(imageComponent)
  }
  const candidateManifest = (await readFile(path.join(candidate.archiveDirectory, 'manifest.txt'), 'utf8'))
    .trimEnd().split(/\r?\n/)
  const sourceRevision = manifestValue(candidateManifest, 'source_revision')
  const sourceDirty = manifestValue(candidateManifest, 'source_worktree_dirty')
  if (!/^[a-f0-9]{40}$/.test(sourceRevision) || !['true', 'false'].includes(sourceDirty)) {
    throw new Error('Candidate has invalid source metadata')
  }

  const parent = path.dirname(path.resolve(destination))
  await mkdir(parent, { recursive: true })
  const temporary = await mkdtemp(path.join(parent, `.pending-${candidate.buildId}-`))
  try {
    const archiveFile = path.join(temporary, 'images.tar')
    await command('docker', ['save', '--output', archiveFile, ...components.map((name) => images[name].ref)])
    const archiveSha256 = await checksum(archiveFile)
    await writeFile(path.join(temporary, 'images.tar.sha256'), `${archiveSha256}  images.tar\n`)
    const manifest = [
      `build_id=${candidate.buildId}`,
      'component=all',
      `promote_component=${component}`,
      `source_revision=${sourceRevision}`,
      `source_worktree_dirty=${sourceDirty}`,
      ...components.map((name) => `image=${images[name].ref} ${images[name].id}`),
      `archive_sha256=${archiveSha256}`,
    ]
    await writeFile(path.join(temporary, 'manifest.txt'), `${manifest.join('\n')}\n`)
    await inspectArchive(temporary)
    if (existsSync(destination)) throw new Error(`Artifact already exists: ${destination}`)
    await rename(temporary, destination)
  } finally {
    await rm(temporary, { recursive: true, force: true })
  }
  console.log(`Service archive: ${destination}`)
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
