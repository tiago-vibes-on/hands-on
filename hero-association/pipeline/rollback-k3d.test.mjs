import assert from 'node:assert/strict'
import { test } from 'node:test'

import { restorePreviousImages } from './rollback-k3d.mjs'

test('restores changed deployments in reverse order and verifies their images', async () => {
  const images = { core: 'new-core', bff: 'new-bff' }
  const calls = []
  const changed = ['core', 'bff']
  const errors = await restorePreviousImages(
    changed,
    { core: 'old-core', bff: 'old-bff' },
    { core: 'new-core', bff: 'new-bff' },
    async (component) => images[component],
    async (component, image) => { calls.push(component); images[component] = image },
  )

  assert.deepEqual(errors, [])
  assert.deepEqual(calls, ['bff', 'core'])
  assert.deepEqual(images, { core: 'old-core', bff: 'old-bff' })
  assert.deepEqual(changed, ['core', 'bff'])
})

test('accepts a deployment already restored without changing it again', async () => {
  const calls = []
  const errors = await restorePreviousImages(
    ['core'], { core: 'old-core' }, { core: 'new-core' },
    async () => 'old-core',
    async (...args) => { calls.push(args) },
  )

  assert.deepEqual(errors, [])
  assert.deepEqual(calls, [])
})

test('does not overwrite a concurrent image change and continues restoring others', async () => {
  const images = { core: 'new-core', bff: 'someone-else-bff' }
  const calls = []
  const errors = await restorePreviousImages(
    ['core', 'bff'],
    { core: 'old-core', bff: 'old-bff' },
    { core: 'new-core', bff: 'new-bff' },
    async (component) => images[component],
    async (component, image) => { calls.push(component); images[component] = image },
  )

  assert.deepEqual(errors, ['bff: image is someone-else-bff, expected old-bff'])
  assert.deepEqual(calls, ['core'])
  assert.deepEqual(images, { core: 'old-core', bff: 'someone-else-bff' })
})

test('reports a failed restore and still attempts the remaining deployments', async () => {
  const images = { core: 'new-core', bff: 'new-bff' }
  const calls = []
  const errors = await restorePreviousImages(
    ['core', 'bff'],
    { core: 'old-core', bff: 'old-bff' },
    { core: 'new-core', bff: 'new-bff' },
    async (component) => images[component],
    async (component, image) => {
      calls.push(component)
      if (component === 'bff') throw new Error('rollout timed out')
      images[component] = image
    },
  )

  assert.deepEqual(errors, ['bff: rollout timed out'])
  assert.deepEqual(calls, ['bff', 'core'])
  assert.deepEqual(images, { core: 'old-core', bff: 'new-bff' })
})

test('rejects a restore that returned successfully but left the target image active', async () => {
  const errors = await restorePreviousImages(
    ['core'], { core: 'old-core' }, { core: 'new-core' },
    async () => 'new-core',
    async () => {},
  )

  assert.deepEqual(errors, ['core: image is new-core, expected old-core'])
})
