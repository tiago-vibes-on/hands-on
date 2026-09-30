import assert from 'node:assert/strict'
import test from 'node:test'
import { fetchSession } from './agency.js'
import { commandExpedition, fetchActiveExpedition, FIRST_FIELD_ID, newUuidV7, startExpedition } from './expedition.js'

const uuidV7 = /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

test('Expedition identifiers are UUIDv7, including their variant', () => {
  assert.match(newUuidV7(), uuidV7)
  assert.notEqual(newUuidV7(), newUuidV7())
})

test('no active Expedition is represented by a null snapshot', async () => {
  const previousFetch = globalThis.fetch
  globalThis.fetch = async (path) => {
    assert.equal(path, '/api/v1/expeditions/active')
    return { ok: true, status: 204 }
  }
  try {
    assert.equal(await fetchActiveExpedition(), null)
  } finally {
    globalThis.fetch = previousFetch
  }
})

test('Start and Continue use the session CSRF token and versioned UUIDv7 commands', async () => {
  const previousFetch = globalThis.fetch
  const requests = []
  globalThis.fetch = async (path, options) => {
    requests.push({ path, options })
    if (path === '/api/v1/session') return { ok: true, status: 200, json: async () => ({ csrfToken: 'session-csrf' }) }
    return { ok: true, status: 200, json: async () => ({ expeditionId: 'run' }) }
  }
  try {
    await fetchSession()
    await startExpedition({ agencyId: 'agency', partyId: 'party' })
    await commandExpedition({ expeditionId: 'run', action: 'continue', expectedVersion: 3 })
    const start = requests[1]
    const continuation = requests[2]
    assert.equal(start.path, '/api/v1/expeditions')
    assert.equal(start.options.headers.get('X-CSRF-TOKEN'), 'session-csrf')
    assert.equal(start.options.headers.get('X-Requested-With'), 'JavaScript')
    const startBody = JSON.parse(start.options.body)
    assert.match(startBody.expeditionId, uuidV7)
    assert.match(startBody.commandId, uuidV7)
    assert.equal(startBody.mapId, FIRST_FIELD_ID)
    assert.equal(startBody.partyId, 'party')
    assert.equal(continuation.path, '/api/v1/expeditions/run/continue')
    assert.equal(continuation.options.headers.get('X-CSRF-TOKEN'), 'session-csrf')
    assert.equal(JSON.parse(continuation.options.body).expectedVersion, 3)
    assert.match(JSON.parse(continuation.options.body).commandId, uuidV7)
  } finally {
    globalThis.fetch = previousFetch
  }
})
