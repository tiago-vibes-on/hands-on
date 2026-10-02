let csrfToken = null

export class ApiRequestError extends Error {
  constructor(message, status, operation = null) {
    super(message)
    this.status = status
    this.operation = operation
  }
}

export async function fetchSession() {
  const session = await request('/api/v1/session')
  csrfToken = session.csrfToken
  return session
}

export async function fetchAccount() {
  return request('/api/v1/account')
}

export async function createManager(displayName) {
  return request('/api/v1/account/manager', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ displayName }),
  })
}

export async function createAgency(name) {
  return request('/api/v1/agencies', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
}

export function beginLogin() {
  window.location.assign('/auth/login')
}

export function beginRegistration() {
  window.location.assign('/auth/login?prompt=create')
}

export function logout() {
  window.location.assign('/auth/logout')
}

export async function fetchAgencyState(agencyId) {
  return request(`/api/v1/agencies/${agencyId}/state`)
}

export async function fetchRecruits() {
  return request('/api/v1/recruits')
}

export async function recruitHero(recruitId) {
  return request(`/api/v1/recruits/${recruitId}/claim`, { method: 'POST' })
}

export async function recruitHeroForAgency({ agencyId, recruitId }) {
  return request(`/api/v1/agencies/${agencyId}/recruits/${recruitId}/claim`, { method: 'POST' })
}

export async function equipHeroRune({ agencyId, heroId, slotIndex, runeId, sourceOwnerType, operationKey }) {
  return request(`/api/v1/agencies/${agencyId}/heroes/${heroId}/rune-slots/${slotIndex}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ runeId, sourceOwnerType, operationKey }),
  })
}

export async function unequipHeroRune({ agencyId, heroId, slotIndex, operationKey }) {
  return request(`/api/v1/agencies/${agencyId}/heroes/${heroId}/rune-slots/${slotIndex}`, {
    method: 'DELETE',
    headers: { 'X-Operation-Key': operationKey },
  })
}

export async function setHeroBorrowingFee({ agencyId, heroId, feeGold }) {
  return request(`/api/v1/agencies/${agencyId}/heroes/${heroId}/borrowing-fee`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ feeGold }),
  })
}

export async function changeHeroActivity({ agencyId, heroId, activity }) {
  return request(`/api/v1/agencies/${agencyId}/heroes/${heroId}/activity`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ activity }),
  })
}

export async function createParty({ agencyId, name }) {
  return request(`/api/v1/agencies/${agencyId}/parties`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
}

export async function addHeroToParty({ agencyId, partyId, heroId }) {
  return request(`/api/v1/agencies/${agencyId}/parties/${partyId}/heroes/${heroId}`, {
    method: 'PUT',
  })
}

export async function removeHeroFromParty({ agencyId, partyId, heroId }) {
  return request(`/api/v1/agencies/${agencyId}/parties/${partyId}/heroes/${heroId}`, {
    method: 'DELETE',
  })
}

export async function startQuest({ agencyId, questId, partyId, expectedBorrowingFeeGold, operationKey }) {
  return request(`/api/v1/agencies/${agencyId}/quests/${questId}/start`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ partyId, expectedBorrowingFeeGold, operationKey }),
  })
}

export async function synchronizeQuestCombat({ agencyId, questId }) {
  return request(`/api/v1/agencies/${agencyId}/quests/${questId}/combat/sync`, {
    method: 'POST',
  })
}

export async function createFeedPost({ agencyId, authorType, authorId, content, itemId, itemQuantity }) {
  return request(`/api/v1/agencies/${agencyId}/feed-posts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ authorType, authorId, content, itemId, itemQuantity }),
  })
}

export async function fetchMarketOrders() {
  return request('/api/v1/market/orders')
}

export async function createMarketOrder({ placementId, ownerType, agencyId, side, itemId, quantity, priceGoldPerItem }) {
  return request('/api/v1/market/orders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ placementId, ownerType, ...(ownerType === 'AGENCY' ? { agencyId } : {}), side, itemId, quantity, priceGoldPerItem }),
  })
}

export async function transferGold({ direction, agencyName, managerName, amountGold, operationKey }) {
  return request('/api/v1/gold-transfers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ direction, agencyName, ...(direction === 'AGENCY_TO_MANAGER' ? { managerName } : {}), amountGold, operationKey }),
  })
}

export async function fetchMarketPlacement(placementId) {
  return request(`/api/v1/market/placements/${placementId}`)
}

export async function fetchMarketOrder(orderId) {
  return request(`/api/v1/market/orders/${orderId}`)
}

export async function cancelMarketOrder({ orderId }) {
  return request(`/api/v1/market/orders/${orderId}`, {
    method: 'DELETE',
  })
}

export async function request(path, options = {}, expectJson = true) {
  const headers = new Headers(options.headers)
  headers.set('X-Requested-With', 'JavaScript')

  if (['POST', 'PUT', 'PATCH', 'DELETE'].includes((options.method ?? 'GET').toUpperCase())) {
    if (csrfToken) {
      headers.set('X-CSRF-TOKEN', csrfToken)
    }
  }

  const response = await fetch(path, { ...options, headers })

  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new ApiRequestError(error?.message ?? `Unable to complete the request (${response.status}).`, response.status, error)
  }

  if (!expectJson) return undefined
  return response.status === 204 ? null : response.json()
}

export function fetchAssetOperation(operationKey) {
  return request(`/api/v1/asset-operations/${operationKey}`)
}
