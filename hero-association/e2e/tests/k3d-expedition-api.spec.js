import { expect, test } from '@playwright/test'

const fieldId = '019c4c00-0006-7000-8000-000000000001'

function uuidV7() {
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

async function snapshotOverSocket(page, expeditionId) {
  return page.evaluate((id) => new Promise((resolve, reject) => {
    const socket = new WebSocket(`wss://${window.location.host}/ws/v1/expeditions/${id}`)
    const timer = setTimeout(() => {
      socket.close()
      reject(new Error('Expedition socket snapshot timed out'))
    }, 15_000)
    socket.onmessage = (event) => {
      const message = JSON.parse(event.data)
      if (message.type === 'snapshot' && message.snapshot?.expeditionId === id) {
        clearTimeout(timer)
        socket.close()
        resolve(message.snapshot)
      }
    }
    socket.onerror = () => {
      clearTimeout(timer)
      reject(new Error('Expedition socket failed'))
    }
  }), expeditionId)
}

test('authenticated k3d Expedition enters, streams, reconnects, continues, and settles', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('manager4@mail.com')
  await page.locator('#password').fill('manager4')
  await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()

  const previous = await page.request.get('/api/v1/expeditions/active')
  if (previous.status() === 200) {
    const run = await previous.json()
    expect(run.expeditionId).toBe(process.env.HERO_ASSOCIATION_K3D_CLEANUP_EXPEDITION_ID)
    const cleanupSession = await (await page.request.get('/api/v1/session')).json()
    const cleanup = await page.request.post(`/api/v1/expeditions/${run.expeditionId}/return`, {
      headers: { 'X-CSRF-TOKEN': cleanupSession.csrfToken, 'X-Requested-With': 'JavaScript' },
      data: { commandId: uuidV7(), expectedVersion: run.stateVersion },
    })
    expect(cleanup.ok(), await cleanup.text()).toBeTruthy()
    await expect.poll(async () => (await page.request.get('/api/v1/expeditions/active')).status(), {
      timeout: 90_000, intervals: [1_000],
    }).toBe(204)
  } else {
    expect(previous.status()).toBe(204)
  }

  const accountResponse = await page.request.get('/api/v1/account')
  expect(accountResponse.status()).toBe(200)
  const account = await accountResponse.json()
  const managerId = account.manager.id
  const agencyId = account.agencyMemberships[0].agencyId
  const stateResponse = await page.request.get(`/api/v1/agencies/${agencyId}/state`)
  expect(stateResponse.status()).toBe(200)
  const state = await stateResponse.json()
  const heroes = state.personalHeroes.filter(hero => hero.ownerManagerId === managerId)
  expect(heroes).toHaveLength(3)
  const warrior = heroes.find(hero => hero.heroClass === 'WARRIOR')
  expect(warrior).toBeTruthy()

  const session = await (await page.request.get('/api/v1/session')).json()
  let headers = { 'X-CSRF-TOKEN': session.csrfToken, 'X-Requested-With': 'JavaScript' }
  let party = state.parties.find(candidate => candidate.ownerManagerId === managerId
    && heroes.every(hero => candidate.heroIds.includes(hero.id)))
  if (!party) {
    const partyName = `k3d Expedition ${Date.now()}`
    const partyResponse = await page.request.post(`/api/v1/agencies/${agencyId}/parties`, {
      headers, data: { name: partyName },
    })
    expect(partyResponse.ok(), await partyResponse.text()).toBeTruthy()
    party = (await partyResponse.json()).parties.find(candidate => candidate.name === partyName)
    expect(party?.id).toBeTruthy()
    for (const hero of heroes) {
      const assigned = await page.request.put(
        `/api/v1/agencies/${agencyId}/parties/${party.id}/heroes/${hero.id}`, { headers })
      expect(assigned.ok(), await assigned.text()).toBeTruthy()
    }
  }

  const expeditionId = uuidV7()
  const entry = await page.request.post('/api/v1/expeditions', {
    headers,
    data: { expeditionId, agencyId, partyId: party.id, mapId: fieldId, commandId: uuidV7() },
  })
  expect(entry.status(), await entry.text()).toBe(201)
  const started = await entry.json()
  expect(started.ownerManagerId).toBe(managerId)
  expect(started.phase).toBe('FIGHTING')
  expect((await snapshotOverSocket(page, expeditionId)).expeditionId).toBe(expeditionId)
  await page.reload()
  expect((await snapshotOverSocket(page, expeditionId)).expeditionId).toBe(expeditionId)
  const refreshedSession = await (await page.request.get('/api/v1/session')).json()
  headers = { 'X-CSRF-TOKEN': refreshedSession.csrfToken, 'X-Requested-With': 'JavaScript' }

  let finished
  await expect.poll(async () => {
    const response = await page.request.get('/api/v1/expeditions/active')
    finished = await response.json()
    return finished.phase
  }, { timeout: 90_000, intervals: [1_000] }).toBe('AWAITING_CONTINUE')
  const continuedResponse = await page.request.post(`/api/v1/expeditions/${expeditionId}/continue`, {
    headers, data: { commandId: uuidV7(), expectedVersion: finished.stateVersion },
  })
  expect(continuedResponse.status(), await continuedResponse.text()).toBe(200)
  const continued = await continuedResponse.json()
  expect(continued.phase).toBe('FIGHTING')
  expect(continued.encounterIndex).toBe(2)

  const returnResponse = await page.request.post(`/api/v1/expeditions/${expeditionId}/return`, {
    headers, data: { commandId: uuidV7(), expectedVersion: continued.stateVersion },
  })
  expect(returnResponse.status(), await returnResponse.text()).toBe(200)
  expect((await returnResponse.json()).returnRequested).toBe(true)

  await expect.poll(async () => (await page.request.get('/api/v1/expeditions/active')).status(), {
    timeout: 90_000, intervals: [1_000],
  }).toBe(204)
  const after = await (await page.request.get(`/api/v1/agencies/${agencyId}/state`)).json()
  expect(after.personalHeroes.find(hero => hero.id === warrior.id).level).toBeGreaterThan(warrior.level)
})
