// Keep the same command across an uncertain response and a page reload.
const fallback = new Map()
export function readPending(key) {
  try { return JSON.parse(sessionStorage.getItem(key) ?? 'null') } catch { return fallback.get(key) ?? null }
}
export function savePending(key, value) {
  fallback.set(key, value)
  try { sessionStorage.setItem(key, JSON.stringify(value)) } catch { /* Memory still supports retries in this tab. */ }
  return value
}
export function clearPending(key) {
  fallback.delete(key)
  try { sessionStorage.removeItem(key) } catch { /* Storage may be unavailable. */ }
}
export function definitiveFailure(error) {
  return error.status >= 400 && error.status < 500 && ![408, 429].includes(error.status)
}
