import assert from 'node:assert/strict'
import test from 'node:test'
import { pendingGoldTransfer } from './goldTransfer.js'
import { transferGold } from './agency.js'

function memoryStorage() {
  const values = new Map()
  return { getItem: (key) => values.get(key), setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key) }
}

test('uncertain transfers reuse their key after recreating the attempt, with normalized names', () => {
  const storage = memoryStorage()
  const transfer = { direction: 'MANAGER_TO_AGENCY', agencyName: ' Dawnwatch Agency ', amountGold: 10 }
  const first = pendingGoldTransfer('manager-1', transfer, storage)
  const retry = pendingGoldTransfer('manager-1', { ...transfer, agencyName: 'DAWNWATCH AGENCY' }, storage)
  assert.equal(first.operationKey, retry.operationKey)
  assert.notEqual(first.operationKey, pendingGoldTransfer('manager-2', transfer, storage).operationKey)
  assert.notEqual(first.operationKey, pendingGoldTransfer('manager-1', { ...transfer, amountGold: 11 }, storage).operationKey)
  first.complete()
  assert.notEqual(first.operationKey, pendingGoldTransfer('manager-1', transfer, storage).operationKey)
})

test('the transfer API forwards the saved operation key unchanged', async () => {
  const originalFetch = globalThis.fetch
  const key = pendingGoldTransfer('manager-1', {
    direction: 'AGENCY_TO_MANAGER', agencyName: 'Dawnwatch Agency', managerName: 'User 2', amountGold: 10,
  }, memoryStorage()).operationKey
  globalThis.fetch = async (path, options) => {
    assert.equal(path, '/api/v1/gold-transfers')
    assert.equal(JSON.parse(options.body).operationKey, key)
    return { ok: true, status: 200, json: async () => ({ operationKey: key }) }
  }
  try {
    await transferGold({ direction: 'AGENCY_TO_MANAGER', agencyName: 'Dawnwatch Agency',
      managerName: 'User 2', amountGold: 10, operationKey: key })
  } finally {
    globalThis.fetch = originalFetch
  }
})
