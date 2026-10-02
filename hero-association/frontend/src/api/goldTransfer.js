import { newUuidV7 } from './uuid.js'

// Keep an uncertain transfer's key across retries and reloads in the same tab.
export function pendingGoldTransfer(managerId, transfer, storage = globalThis.sessionStorage) {
  const fingerprint = JSON.stringify([managerId, transfer.direction,
    transfer.agencyName.trim().toLowerCase(),
    transfer.direction === 'AGENCY_TO_MANAGER' ? transfer.managerName.trim().toLowerCase() : null,
    transfer.amountGold])
  const storageKey = `hero-association.gold-transfer:${fingerprint}`
  let operationKey = storage.getItem(storageKey)
  if (!operationKey) {
    operationKey = newUuidV7()
    storage.setItem(storageKey, operationKey)
  }
  return { operationKey, complete: () => storage.removeItem(storageKey) }
}
