import { execFile } from 'node:child_process'
import { createHash } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { readFile, realpath } from 'node:fs/promises'
import path from 'node:path'
import { promisify } from 'node:util'

const executeFile = promisify(execFile)
const components = ['core', 'bff', 'expedition', 'frontend']

function singleManifestValue(lines, name) {
  const values = lines.filter((line) => line.startsWith(`${name}=`))
  if (values.length !== 1) {
    throw new Error(`Archive manifest must contain exactly one ${name} value`)
  }
  return values[0].slice(name.length + 1)
}

async function sha256(filePath) {
  const hash = createHash('sha256')
  for await (const chunk of createReadStream(filePath)) {
    hash.update(chunk)
  }
  return hash.digest('hex')
}

async function inspectImage(ref) {
  try {
    const { stdout } = await executeFile('docker', ['image', 'inspect', '--format', '{{.Id}}', ref])
    return stdout.trim()
  } catch (error) {
    if (error.code === 1) {
      return null
    }
    throw error
  }
}

export async function inspectArchive(directory) {
  const archiveDirectory = await realpath(directory)
  const lines = (await readFile(path.join(archiveDirectory, 'manifest.txt'), 'utf8'))
    .trimEnd().split(/\r?\n/)
  const buildId = singleManifestValue(lines, 'build_id')
  if (!/^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/.test(buildId)) {
    throw new Error(`Invalid archive build ID: ${buildId}`)
  }
  const component = singleManifestValue(lines, 'component')
  if (component !== 'all' && !components.includes(component)) {
    throw new Error(`Unsupported archive component: ${component}`)
  }
  const promotions = lines.filter((line) => line.startsWith('promote_component='))
  if (promotions.length > 1 || (promotions.length &&
      (component !== 'all' || !components.includes(promotions[0].slice('promote_component='.length))))) {
    throw new Error('Invalid archive promotion component')
  }
  const promoteComponent = promotions.length ? promotions[0].slice('promote_component='.length) : null

  const archiveSha256 = singleManifestValue(lines, 'archive_sha256')
  const checksum = await readFile(path.join(archiveDirectory, 'images.tar.sha256'), 'utf8')
  const checksumMatch = /^([a-f0-9]{64})  images\.tar\r?\n?$/.exec(checksum)
  if (!checksumMatch || checksumMatch[1] !== archiveSha256) {
    throw new Error('Archive manifest and checksum file disagree')
  }
  if (await sha256(path.join(archiveDirectory, 'images.tar')) !== archiveSha256) {
    throw new Error('Image archive SHA-256 verification failed')
  }

  const imageLines = lines.filter((line) => line.startsWith('image='))
  const expectedComponents = component === 'all' ? components : [component]
  if (imageLines.length !== expectedComponents.length) {
    throw new Error(`Archive manifest must contain exactly ${expectedComponents.join(', ')} image(s)`)
  }
  const images = {}
  for (const imageComponent of expectedComponents) {
    const prefix = `hero-association-${imageComponent}:`
    const matches = imageLines.filter((line) => line.startsWith(`image=${prefix}`))
    const match = matches.length === 1 && /^image=(\S+) (sha256:[a-f0-9]{64})$/.exec(matches[0])
    if (!match) {
      throw new Error(`Archive manifest is missing exactly one valid ${imageComponent} image ID`)
    }
    const [, ref, id] = match
    if (!new RegExp(`^hero-association-${imageComponent}:[A-Za-z0-9_.-]+$`).test(ref) ||
        ((!promoteComponent || imageComponent === promoteComponent) && ref !== `${prefix}${buildId}`)) {
      throw new Error(`Archive has invalid ${imageComponent} image reference: ${ref}`)
    }
    images[imageComponent] = { ref, id }
  }

  return { archiveDirectory, archiveSha256, buildId, component, promoteComponent, images }
}

export async function prepareArchive(directory) {
  const archive = await inspectArchive(directory)
  const { archiveDirectory, images } = archive
  for (const { ref, id } of Object.values(images)) {
    const existingId = await inspectImage(ref)
    if (existingId && existingId !== id) {
      throw new Error(`Local image tag ${ref} already points to another image; refusing to overwrite it`)
    }
  }

  await executeFile('docker', ['load', '--input', path.join(archiveDirectory, 'images.tar')], {
    maxBuffer: 10 * 1024 * 1024,
  })
  for (const { ref, id } of Object.values(images)) {
    if (await inspectImage(ref) !== id) {
      throw new Error(`Loaded image ${ref} does not match the archive manifest`)
    }
  }
  return archive
}

export async function requirePassingK3dE2EVerification(archive) {
  if (archive.component !== 'all') {
    throw new Error('Deployment requires a complete four-image archive')
  }
  let record
  try {
    record = JSON.parse(await readFile(path.join(archive.archiveDirectory, 'k3d-e2e-verification.json'), 'utf8'))
  } catch {
    throw new Error('Archive has no readable k3d E2E verification record; run test-isolated-stack.sh first')
  }
  if (record.version !== 1 || record.suite !== 'k3d-isolated' ||
      record.result !== 'passed' || record.buildId !== archive.buildId ||
      record.archiveSha256 !== archive.archiveSha256 ||
      Object.keys(record.imageIds ?? {}).sort().join(',') !== [...components].sort().join(',') ||
      components.some((component) => record.imageIds[component] !== archive.images[component].id)) {
    throw new Error('Archive k3d E2E verification does not match the exact images and checksum being deployed')
  }
  return record
}
