#!/usr/bin/env node

import { createWriteStream } from 'node:fs'
import { mkdir, mkdtemp } from 'node:fs/promises'
import path from 'node:path'
import { Readable } from 'node:stream'
import { pipeline } from 'node:stream/promises'
import { fileURLToPath } from 'node:url'
import { prepareArchive, requirePassingE2EVerification } from '../e2e/archive-images.js'

// This command is deliberately bound to the local emulator, never ambient AWS.
const endpoint = 'http://127.0.0.1:4566'
const bucket = 'hero-association-builds'
const files = ['manifest.txt', 'images.tar.sha256', 'e2e-verification.json', 'images.tar']
const buildIdPattern = /^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/

async function main() {
  const buildId = process.argv[2]
  if (process.argv.length !== 3 || !buildIdPattern.test(buildId)) {
    throw new Error('Usage: node fetch-floci.mjs <build-id>')
  }

  const health = await fetch(`${endpoint}/_localstack/health`, { signal: AbortSignal.timeout(10_000) })
  if (!health.ok) throw new Error(`Floci health check returned HTTP ${health.status}`)

  const artifactsRoot = path.join(path.dirname(fileURLToPath(import.meta.url)), 'artifacts')
  await mkdir(artifactsRoot, { recursive: true })
  const destination = await mkdtemp(path.join(artifactsRoot, `floci-${buildId}-`))
  for (const name of files) {
    const url = `${endpoint}/${bucket}/builds/${buildId}/all/${name}`
    const response = await fetch(url)
    if (!response.ok || !response.body) {
      throw new Error(`Floci S3 download of ${name} returned HTTP ${response.status}; partial download is at ${destination}`)
    }
    await pipeline(Readable.fromWeb(response.body), createWriteStream(path.join(destination, name)))
  }

  const archive = await prepareArchive(destination)
  if (archive.buildId !== buildId) throw new Error('Downloaded manifest build ID does not match the requested build')
  await requirePassingE2EVerification(archive)
  console.log(`Verified Floci archive: ${destination}`)
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
