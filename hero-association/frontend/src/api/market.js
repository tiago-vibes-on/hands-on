import { newUuidV7 } from './uuid.js'

const key = (managerId) => `hero-association.market:${managerId}`
export function marketAttempts(managerId, storage = globalThis.sessionStorage) {
  return JSON.parse(storage.getItem(key(managerId)) ?? '[]')
}
function save(managerId, entries, storage) {
  storage.setItem(key(managerId), JSON.stringify(entries))
  return entries
}
export function pendingPlacement(managerId, order, storage = globalThis.sessionStorage) {
  const fingerprint = JSON.stringify([order.ownerType, order.agencyId ?? null, order.side,
    order.itemId, order.quantity, order.priceGoldPerItem])
  const entries = marketAttempts(managerId, storage)
  let entry = entries.find((entry) => entry.kind === 'placement' && entry.fingerprint === fingerprint)
  if (!entry) {
    entry = { kind: 'placement', id: newUuidV7(), fingerprint, status: 'PENDING_RESERVATION' }
    save(managerId, [...entries, entry], storage)
  }
  return entry
}
export function pendingCancellation(managerId, orderId, storage = globalThis.sessionStorage) {
  const entries = marketAttempts(managerId, storage)
  if (!entries.some((entry) => entry.kind === 'cancellation' && entry.id === orderId)) {
    save(managerId, [...entries, { kind: 'cancellation', id: orderId, status: 'PENDING_CANCEL' }], storage)
  }
}
export function finishMarketAttempt(managerId, kind, id, storage = globalThis.sessionStorage) {
  return save(managerId, marketAttempts(managerId, storage)
    .filter((entry) => entry.kind !== kind || entry.id !== id), storage)
}
export function isMarketAttemptTerminal(entry, status) {
  return entry.kind === 'placement'
    ? ['OPEN', 'REJECTED', 'ABANDONED', 'CONFLICT'].includes(status)
    : ['CANCELLED', 'FILLED', 'CONFLICT'].includes(status)
}
