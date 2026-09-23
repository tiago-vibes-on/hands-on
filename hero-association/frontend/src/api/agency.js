let csrfToken = null

export class ApiRequestError extends Error {
  constructor(message, status) {
    super(message)
    this.status = status
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

export function beginLogin() {
  window.location.assign('/auth/login')
}

export function logout() {
  window.location.assign('/auth/logout')
}

export async function fetchAgencyState(agencyId) {
  return request(`/api/v1/agencies/${agencyId}/state`)
}

export async function equipHeroRune({ agencyId, heroId, slotIndex, runeId }) {
  return request(`/api/v1/agencies/${agencyId}/heroes/${heroId}/rune-slots/${slotIndex}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ runeId }),
  })
}

export async function unequipHeroRune({ agencyId, heroId, slotIndex }) {
  return request(`/api/v1/agencies/${agencyId}/heroes/${heroId}/rune-slots/${slotIndex}`, {
    method: 'DELETE',
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

export async function startQuest({ agencyId, questId, partyId }) {
  return request(`/api/v1/agencies/${agencyId}/quests/${questId}/start`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ partyId }),
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

export async function createMarketOrder({ agencyId, side, itemId, quantity, priceGoldPerItem }) {
  return request(`/api/v1/agencies/${agencyId}/market-orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ side, itemId, quantity, priceGoldPerItem }),
  })
}

export async function cancelMarketOrder({ agencyId, orderId }) {
  return request(`/api/v1/agencies/${agencyId}/market-orders/${orderId}`, {
    method: 'DELETE',
  })
}

async function request(path, options = {}, expectJson = true) {
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
    throw new ApiRequestError(error?.message ?? `Unable to complete the request (${response.status}).`, response.status)
  }

  return expectJson ? response.json() : undefined
}
