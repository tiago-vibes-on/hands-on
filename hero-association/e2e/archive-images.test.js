import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'

import { inspectArchive } from './archive-images.js'

const imageId = 'sha256:' + 'a'.repeat(64)
const buildId = 'worktree-core-1-abcdef01-20260929T120000'
const image = (name, tag = buildId) => `image=hero-association-${name}:${tag} ${imageId}`

async function fixture(lines) {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'hero-archive-test-'))
  const tar = Buffer.from('test-only-archive')
  const checksum = createHash('sha256').update(tar).digest('hex')
  await writeFile(path.join(directory, 'images.tar'), tar)
  await writeFile(path.join(directory, 'images.tar.sha256'), `${checksum}  images.tar\n`)
  await writeFile(path.join(directory, 'manifest.txt'), [
    `build_id=${buildId}`,
    ...lines,
    `archive_sha256=${checksum}`,
  ].join('\n') + '\n')
  return directory
}

test('accepts a single-service candidate archive', async (t) => {
  const directory = await fixture(['component=core', image('core')])
  t.after(() => rm(directory, { recursive: true, force: true }))
  const archive = await inspectArchive(directory)
  assert.equal(archive.component, 'core')
  assert.equal(archive.promoteComponent, null)
  assert.deepEqual(Object.keys(archive.images), ['core'])
})

test('accepts a full service archive with prior baseline tags', async (t) => {
  const directory = await fixture([
    'component=all',
    'promote_component=core',
    image('core'),
    image('bff', 'main-bff-17-previous'),
    image('frontend', 'main-frontend-16-previous'),
  ])
  t.after(() => rm(directory, { recursive: true, force: true }))
  const archive = await inspectArchive(directory)
  assert.equal(archive.promoteComponent, 'core')
  assert.equal(archive.images.bff.ref, 'hero-association-bff:main-bff-17-previous')
})

test('keeps the existing full-build archive format', async (t) => {
  const directory = await fixture(['component=all', ...['core', 'bff', 'frontend'].map((name) => image(name))])
  t.after(() => rm(directory, { recursive: true, force: true }))
  assert.equal((await inspectArchive(directory)).promoteComponent, null)
})

test('rejects a mismatched promoted image tag', async (t) => {
  const directory = await fixture([
    'component=all', 'promote_component=core', image('core', 'wrong'), image('bff'), image('frontend'),
  ])
  t.after(() => rm(directory, { recursive: true, force: true }))
  await assert.rejects(inspectArchive(directory), /invalid core image reference/)
})

test('rejects promotion metadata on a one-image archive', async (t) => {
  const directory = await fixture(['component=core', 'promote_component=core', image('core')])
  t.after(() => rm(directory, { recursive: true, force: true }))
  await assert.rejects(inspectArchive(directory), /Invalid archive promotion component/)
})

test('rejects a wrong image count', async (t) => {
  const directory = await fixture(['component=all', image('core'), image('bff')])
  t.after(() => rm(directory, { recursive: true, force: true }))
  await assert.rejects(inspectArchive(directory), /exactly core, bff, frontend image/)
})
