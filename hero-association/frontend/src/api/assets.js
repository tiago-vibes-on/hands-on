import { newUuidV7 } from './uuid.js'

const key = (managerId) => `hero-association.assets:${managerId}`
export function assetAttempts(managerId, storage = globalThis.sessionStorage) {
  return JSON.parse(storage.getItem(key(managerId)) ?? '[]')
}
export function pendingAssetOperation(managerId, kind, payload, storage = globalThis.sessionStorage) {
  const entries = assetAttempts(managerId, storage)
  const fingerprint = JSON.stringify([kind, payload])
  let attempt = entries.find((entry) => entry.fingerprint === fingerprint)
  if (!attempt) {
    attempt = { operationKey: newUuidV7(), kind, payload, fingerprint }
    storage.setItem(key(managerId), JSON.stringify([...entries, attempt]))
  }
  return attempt
}
export function finishAssetOperation(managerId, operationKey, storage = globalThis.sessionStorage) {
  storage.setItem(key(managerId), JSON.stringify(assetAttempts(managerId, storage)
    .filter((entry) => entry.operationKey !== operationKey)))
}
