import { execFile } from 'node:child_process'
import { createHash } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { readFile, realpath, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { promisify } from 'node:util'

const executeFile = promisify(execFile)
const components = ['core', 'bff', 'frontend']

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
  if (singleManifestValue(lines, 'component') !== 'all') {
    throw new Error('Browser E2E requires one complete all-component archive')
  }

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
  if (imageLines.length !== components.length) {
    throw new Error('Archive manifest must contain exactly the Core, BFF, and frontend images')
  }
  const images = {}
  for (const component of components) {
    const ref = `hero-association-${component}:${buildId}`
    const matches = imageLines.filter((line) => line.startsWith(`image=${ref} `))
    const match = matches.length === 1 && /^image=\S+ (sha256:[a-f0-9]{64})$/.exec(matches[0])
    if (!match) {
      throw new Error(`Archive manifest is missing exactly one valid ${ref} image ID`)
    }
    images[component] = { ref, id: match[1] }
  }

  return { archiveDirectory, archiveSha256, buildId, images }
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

export async function recordE2EVerification(archive, status) {
  if (status !== 'pending' && status !== 'passed') {
    throw new Error(`Unsupported E2E verification status: ${status}`)
  }
  const record = {
    version: 1,
    result: status,
    buildId: archive.buildId,
    archiveSha256: archive.archiveSha256,
    imageIds: Object.fromEntries(
      components.map((component) => [component, archive.images[component].id]),
    ),
    recordedAt: new Date().toISOString(),
  }
  await writeFile(
    path.join(archive.archiveDirectory, 'e2e-verification.json'),
    `${JSON.stringify(record, null, 2)}\n`,
  )
}

export async function requirePassingE2EVerification(archive) {
  let record
  try {
    record = JSON.parse(await readFile(path.join(archive.archiveDirectory, 'e2e-verification.json'), 'utf8'))
  } catch {
    throw new Error('Archive has no readable E2E verification record; run npm run test:archive first')
  }
  if (record.version !== 1 || record.result !== 'passed' ||
      record.buildId !== archive.buildId || record.archiveSha256 !== archive.archiveSha256 ||
      Object.keys(record.imageIds ?? {}).sort().join(',') !== [...components].sort().join(',') ||
      components.some((component) => record.imageIds[component] !== archive.images[component].id)) {
    throw new Error('Archive E2E verification does not match the exact images and checksum being deployed')
  }
  return record
}
