import assert from 'node:assert/strict'
import test from 'node:test'
import { assetAttempts, pendingAssetOperation, finishAssetOperation } from './assets.js'

test('equipment and Quest operation keys survive response loss and reload, scoped to actor and request', () => {
  const values = new Map()
  const storage = { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) }
  const payload = { agencyId: 'agency', heroId: 'hero', slotIndex: 0, runeId: 'rune', sourceOwnerType: 'AGENCY' }
  const first = pendingAssetOperation('manager', 'RUNE_EQUIP', payload, storage)
  assert.equal(pendingAssetOperation('manager', 'RUNE_EQUIP', payload, storage).operationKey, first.operationKey)
  assert.equal(assetAttempts('manager', storage)[0].operationKey, first.operationKey)
  assert.notEqual(pendingAssetOperation('other', 'RUNE_EQUIP', payload, storage).operationKey, first.operationKey)
  assert.notEqual(pendingAssetOperation('manager', 'RUNE_UNEQUIP', payload, storage).operationKey, first.operationKey)
  const quest = pendingAssetOperation('manager', 'QUEST_START', { partyId: 'party', questId: 'quest', expectedBorrowingFeeGold: 25 }, storage)
  assert.equal(pendingAssetOperation('manager', 'QUEST_START', quest.payload, storage).operationKey, quest.operationKey)
  finishAssetOperation('manager', first.operationKey, storage)
  assert.notEqual(pendingAssetOperation('manager', 'RUNE_EQUIP', payload, storage).operationKey, first.operationKey)
  assert.equal(assetAttempts('manager', storage).some((entry) => entry.operationKey === quest.operationKey), true)
})
