import { request } from './agency.js'
import { newUuidV7 } from './uuid.js'
export { newUuidV7 } from './uuid.js'

export const FIRST_FIELD_ID = '019c4c00-0006-7000-8000-000000000001'

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
