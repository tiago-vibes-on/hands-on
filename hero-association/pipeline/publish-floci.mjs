#!/usr/bin/env node

import { createHash } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { stat } from 'node:fs/promises'
import path from 'node:path'
import { Readable } from 'node:stream'
import { inspectArchive, prepareArchive, requirePassingE2EVerification } from '../e2e/archive-images.js'

// Intentionally fixed to the loopback-only emulator: this command must never
// publish to a real AWS endpoint because of ambient AWS CLI configuration.
const endpoint = 'http://127.0.0.1:4566'
const bucket = 'hero-association-builds'
const files = ['images.tar', 'images.tar.sha256', 'manifest.txt', 'e2e-verification.json']

async function digest(stream) {
  const hash = createHash('sha256')
  for await (const chunk of stream) hash.update(chunk)
  return hash.digest('hex')
}

async function ensureBucket() {
  const response = await fetch(`${endpoint}/${bucket}`, { method: 'HEAD' })
  if (response.ok) return
  if (response.status !== 404) throw new Error(`S3 bucket check returned HTTP ${response.status}`)
  const created = await fetch(`${endpoint}/${bucket}`, { method: 'PUT' })
  if (!created.ok) throw new Error(`S3 bucket creation returned HTTP ${created.status}`)
}

async function publishFile(archive, name) {
  const localPath = path.join(archive.archiveDirectory, name)
  const key = `builds/${archive.buildId}/all/${name}`
  const url = `${endpoint}/${bucket}/${key}`
  const expectedDigest = await digest(createReadStream(localPath))
  const existing = await fetch(url, { method: 'HEAD' })
  if (existing.status !== 404 && !existing.ok) {
    throw new Error(`S3 check for ${key} returned HTTP ${existing.status}`)
  }
  if (existing.status === 404) {
    const file = await stat(localPath)
    const response = await fetch(url, {
      method: 'PUT',
      headers: { 'content-length': String(file.size), 'content-type': 'application/octet-stream' },
      body: Readable.toWeb(createReadStream(localPath)),
      duplex: 'half',
    })
    if (!response.ok) throw new Error(`S3 upload of ${key} returned HTTP ${response.status}`)
  }

  const downloaded = await fetch(url)
  if (!downloaded.ok || !downloaded.body) {
    throw new Error(`S3 verification of ${key} returned HTTP ${downloaded.status}`)
  }
  const actualDigest = await digest(Readable.fromWeb(downloaded.body))
  if (actualDigest !== expectedDigest) {
    throw new Error(`S3 object ${key} differs from the verified local archive; refusing to overwrite it`)
  }
  console.log(`${existing.status === 404 ? 'Uploaded' : 'Verified existing'} ${key} (${actualDigest})`)
}

async function main() {
  if (process.argv.length !== 3) {
    throw new Error('Usage: node publish-floci.mjs artifacts/<build-id>/all')
  }
  const archive = await inspectArchive(process.argv[2])
  if (archive.promoteComponent) throw new Error('K3d service archives are not portable Floci artifacts')
  await prepareArchive(archive.archiveDirectory)
  await requirePassingE2EVerification(archive)

  const health = await fetch(`${endpoint}/_localstack/health`, { signal: AbortSignal.timeout(10_000) })
  if (!health.ok) throw new Error(`Floci health check returned HTTP ${health.status}`)
  await ensureBucket()
  for (const name of files) await publishFile(archive, name)
  console.log(`Verified archive ${archive.buildId} is stored in Floci S3; application deployment is a separate step.`)
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
