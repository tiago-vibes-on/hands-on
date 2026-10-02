import assert from 'node:assert/strict'
import test from 'node:test'
import { pendingPlacement, pendingCancellation, marketAttempts, finishMarketAttempt, isMarketAttemptTerminal } from './market.js'
function storage() {
  const values = new Map()
  return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) }
}
const order = { ownerType: 'MANAGER', side: 'BUY', itemId: 'item', quantity: 2, priceGoldPerItem: 10 }
test('a lost placement response keeps its ID across retry and reload, scoped to the actor and payload', () => {
  const store = storage()
  const first = pendingPlacement('manager-a', order, store)
  assert.equal(pendingPlacement('manager-a', order, store).id, first.id)
  assert.equal(marketAttempts('manager-a', store)[0].id, first.id)
  assert.notEqual(pendingPlacement('manager-b', order, store).id, first.id)
  assert.notEqual(pendingPlacement('manager-a', { ...order, quantity: 3 }, store).id, first.id)
  finishMarketAttempt('manager-a', 'placement', first.id, store)
  assert.notEqual(pendingPlacement('manager-a', order, store).id, first.id)
})
test('cancellation remains visible until closure and does not duplicate after retry', () => {
  const store = storage()
  pendingCancellation('manager-a', 'order-a', store)
  pendingCancellation('manager-a', 'order-a', store)
  assert.equal(marketAttempts('manager-a', store).length, 1)
  assert.equal(isMarketAttemptTerminal({ kind: 'cancellation' }, 'PENDING_CANCEL'), false)
  assert.equal(isMarketAttemptTerminal({ kind: 'cancellation' }, 'CANCELLED'), true)
  assert.equal(isMarketAttemptTerminal({ kind: 'placement' }, 'PENDING_ABORT'), false)
  assert.equal(isMarketAttemptTerminal({ kind: 'placement' }, 'ABANDONED'), true)
})
