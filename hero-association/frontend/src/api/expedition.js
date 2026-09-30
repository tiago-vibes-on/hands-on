import { request } from './agency.js'

export const FIRST_FIELD_ID = '019c4c00-0006-7000-8000-000000000001'

export function newUuidV7() {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  const timestamp = Date.now()
  for (let index = 0; index < 6; index += 1) {
    bytes[index] = Math.floor(timestamp / 2 ** (8 * (5 - index))) & 0xff
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x70
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export function fetchActiveExpedition() {
  return request('/api/v1/expeditions/active')
}

export function startExpedition({ agencyId, partyId }) {
  return request('/api/v1/expeditions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ expeditionId: newUuidV7(), agencyId, partyId, mapId: FIRST_FIELD_ID, commandId: newUuidV7() }),
  })
}

export function commandExpedition({ expeditionId, action, expectedVersion }) {
  if (action !== 'continue' && action !== 'return') {
    throw new Error('Unsupported Expedition command.')
  }
  return request(`/api/v1/expeditions/${expeditionId}/${action}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ commandId: newUuidV7(), expectedVersion }),
  })
}

export function connectExpedition(expeditionId) {
  const url = new URL(`/ws/v1/expeditions/${expeditionId}`, window.location.href)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return new WebSocket(url)
}
