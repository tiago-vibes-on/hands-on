import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'

import { inspectArchive, requirePassingK3dE2EVerification } from './archive-images.js'

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

test('accepts an Expedition candidate archive', async (t) => {
  const directory = await fixture(['component=expedition', image('expedition')])
  t.after(() => rm(directory, { recursive: true, force: true }))
  const archive = await inspectArchive(directory)
  assert.equal(archive.component, 'expedition')
  assert.deepEqual(Object.keys(archive.images), ['expedition'])
})

test('accepts a full service archive with prior baseline tags', async (t) => {
  const directory = await fixture([
    'component=all',
    'promote_component=core',
    image('core'),
    image('bff', 'main-bff-17-previous'),
    image('expedition', 'main-expedition-18-previous'),
    image('frontend', 'main-frontend-16-previous'),
  ])
  t.after(() => rm(directory, { recursive: true, force: true }))
  const archive = await inspectArchive(directory)
  assert.equal(archive.promoteComponent, 'core')
  assert.equal(archive.images.bff.ref, 'hero-association-bff:main-bff-17-previous')
  assert.equal(archive.images.expedition.ref, 'hero-association-expedition:main-expedition-18-previous')
})

test('accepts the four-image full-build archive format', async (t) => {
  const directory = await fixture(['component=all', ...['core', 'bff', 'expedition', 'frontend'].map((name) => image(name))])
  t.after(() => rm(directory, { recursive: true, force: true }))
  assert.equal((await inspectArchive(directory)).promoteComponent, null)
})

test('rejects a mismatched promoted image tag', async (t) => {
  const directory = await fixture([
    'component=all', 'promote_component=core', image('core', 'wrong'), image('bff'), image('expedition'), image('frontend'),
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
  await assert.rejects(inspectArchive(directory), /exactly core, bff, expedition, frontend image/)
})

test('requires exact passing isolated k3d evidence for a four-image archive', async (t) => {
  const directory = await fixture([
    'component=all',
    ...['core', 'bff', 'expedition', 'frontend'].map((name) => image(name)),
  ])
  t.after(() => rm(directory, { recursive: true, force: true }))
  const archive = await inspectArchive(directory)
  await writeFile(path.join(directory, 'e2e-verification.json'), JSON.stringify({ result: 'passed' }))
  await assert.rejects(requirePassingK3dE2EVerification(archive), /no readable k3d E2E verification/)

  const evidencePath = path.join(directory, 'k3d-e2e-verification.json')
  const record = {
    version: 1,
    suite: 'k3d-isolated',
    result: 'passed',
    buildId: archive.buildId,
    archiveSha256: archive.archiveSha256,
    imageIds: Object.fromEntries(Object.entries(archive.images).map(([name, value]) => [name, value.id])),
  }
  const save = () => writeFile(evidencePath, JSON.stringify(record))
  await save()
  assert.deepEqual(await requirePassingK3dE2EVerification(archive), record)

  record.result = 'pending'
  await save()
  await assert.rejects(requirePassingK3dE2EVerification(archive), /does not match/)
  record.result = 'passed'
  record.suite = 'other-suite'
  await save()
  await assert.rejects(requirePassingK3dE2EVerification(archive), /does not match/)
  record.suite = 'k3d-isolated'
  record.imageIds.bff = 'sha256:' + 'b'.repeat(64)
  await save()
  await assert.rejects(requirePassingK3dE2EVerification(archive), /does not match/)
  record.imageIds.bff = archive.images.bff.id
  record.archiveSha256 = 'b'.repeat(64)
  await save()
  await assert.rejects(requirePassingK3dE2EVerification(archive), /does not match/)
})
