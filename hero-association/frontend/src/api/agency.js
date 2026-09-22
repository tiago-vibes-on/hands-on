const AGENCY_ID = '019c4c00-0001-7000-8000-000000000001'

export async function fetchAgencyState() {
  return request(`/api/v1/agencies/${AGENCY_ID}/state`)
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

async function request(path, options) {
  const response = await fetch(path, options)

  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new Error(error?.message ?? `Unable to complete the request (${response.status}).`)
  }

  return response.json()
}
