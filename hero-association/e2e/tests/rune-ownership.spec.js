import { expect, test } from '@playwright/test'

function uuidV7() {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  for (let index = 0; index < 6; index++) bytes[index] = Math.floor(Date.now() / 2 ** (8 * (5 - index))) & 0xff
  bytes[6] = (bytes[6] & 0x0f) | 0x70
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

const agencyId = '019c4c00-0001-7000-8000-000000000001'
const user1WarriorId = '019c4c00-0030-7001-8000-000000000001'

async function signIn(page, email, password) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill(email)
  await page.locator('#password').fill(password)
  await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
}

async function agencyState(page) {
  const response = await page.request.get(`/api/v1/agencies/${agencyId}/state`)
  expect(response.status()).toBe(200)
  return response.json()
}

function quantity(inventory, code) {
  return inventory.find((entry) => entry.rune.code === code)?.quantity ?? 0
}

test('recovers a lost equipment response after reload, then moves the rune between owners', async ({ page }) => {
  await signIn(page, 'user1@mail.com', 'user1')
  const initial = await agencyState(page)
  const personalWarrior = initial.personalHeroes.find((hero) => hero.id === user1WarriorId)
  expect(personalWarrior?.runeSlots[0].rune).toBeNull()
  expect(quantity(initial.runeInventory, 'attack-rune')).toBe(1)

  let lostRequest
  await page.route('**/api/v1/agencies/*/heroes/*/rune-slots/*', async (route) => {
    if (route.request().method() !== 'PUT' || lostRequest) return route.continue()
    const response = await route.fetch()
    expect(response.status()).toBe(200)
    lostRequest = { url: route.request().url(), data: route.request().postDataJSON() }
    await route.fulfill({ status: 503, contentType: 'application/json',
      body: JSON.stringify({ message: 'Equipment response lost after commit.' }) })
  })

  await page.getByRole('button', { name: 'Heroes', exact: true }).click()
  const agencySlot = page.getByRole('group', { name: 'Emberveil rune slots' })
    .getByRole('button', { name: /Emberveil rune slot 5/ })
  await agencySlot.click()
  await page.getByRole('dialog').getByRole('region', { name: 'Agency inventory' })
    .getByRole('button', { name: /Attack Rune/ }).click()
  await expect(page.getByText('Equipment response lost after commit.')).toBeVisible()
  expect(lostRequest.data.operationKey).toBeTruthy()
  await page.reload()
  await page.getByRole('button', { name: 'Heroes', exact: true }).click()
  await expect(agencySlot).toHaveAttribute('aria-label', /Attack Rune/)
  await expect.poll(async () => {
    const result = await page.request.get(`/api/v1/asset-operations/${lostRequest.data.operationKey}`)
    return (await result.json()).status
  }).toBe('APPLIED')
  await expect.poll(() => page.evaluate(() => Object.keys(sessionStorage)
    .filter((key) => key.startsWith('hero-association.assets:'))
    .flatMap((key) => JSON.parse(sessionStorage.getItem(key))).length)).toBe(0)
  const sessionAfterReload = await (await page.request.get('/api/v1/session')).json()
  const replay = await page.request.put(lostRequest.url, {
    headers: { 'X-CSRF-TOKEN': sessionAfterReload.csrfToken }, data: lostRequest.data,
  })
  expect(replay.status()).toBe(200)
  // A direct session request rotates the CSRF cookie; reload to refresh the UI's cached token.
  await page.reload()
  await page.getByRole('button', { name: 'Heroes', exact: true }).click()
  await expect(agencySlot).toHaveAttribute('aria-label', /Attack Rune/)
  const equipped = await agencyState(page)
  expect(equipped.heroes.find((hero) => hero.alias === 'Emberveil').runeSlots[4].rune.code).toBe('attack-rune')
  expect(quantity(equipped.runeInventory, 'attack-rune')).toBe(0)

  await agencySlot.click()
  await page.getByRole('dialog').getByRole('button', { name: 'Unequip' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(agencySlot).toHaveAttribute('aria-label', /empty/)
  const unequipped = await agencyState(page)
  expect(quantity(unequipped.personalRuneInventory, 'attack-rune')).toBe(1)
  expect(quantity(unequipped.runeInventory, 'attack-rune')).toBe(0)

  const personalSlot = page.getByRole('group', { name: `${personalWarrior.alias} rune slots` })
    .getByRole('button', { name: new RegExp(`${personalWarrior.alias} rune slot 1`) })
  await personalSlot.click()
  await page.getByRole('dialog').getByRole('region', { name: 'Personal inventory' })
    .getByRole('button', { name: /Attack Rune/ }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(personalSlot).toHaveAttribute('aria-label', /Attack Rune/)
  await page.reload()
  await page.getByRole('button', { name: 'Heroes', exact: true }).click()
  await expect(page.getByRole('group', { name: `${personalWarrior.alias} rune slots` })
    .getByRole('button', { name: /Attack Rune/ })).toBeVisible()
  const finalState = await agencyState(page)
  expect(finalState.personalHeroes.find((hero) => hero.id === user1WarriorId).runeSlots[0].rune.code)
    .toBe('attack-rune')
  expect(quantity(finalState.personalRuneInventory, 'attack-rune')).toBe(0)
})

test('allows a member to use agency runes but blocks another Manager’s Hero and away Heroes', async ({ page }) => {
  test.setTimeout(180_000)
  await signIn(page, 'manager1@mail.com', 'manager1')
  const initial = await agencyState(page)
  const account = await (await page.request.get('/api/v1/account')).json()
  const vitalityRune = initial.runeInventory.find((entry) => entry.rune.code === 'vitality-rune')?.rune
  const oakshield = initial.heroes.find((hero) => hero.alias === 'Oakshield')
  const memberWarrior = initial.personalHeroes.find((hero) => hero.ownerManagerId === account.manager.id
    && hero.heroClass === 'WARRIOR')
  expect(vitalityRune).toBeTruthy()
  expect(quantity(initial.runeInventory, 'vitality-rune')).toBe(1)
  expect(oakshield?.runeSlots[4].rune).toBeNull()
  expect(memberWarrior?.runeSlots[4].rune).toBeNull()

  await page.getByRole('button', { name: 'Heroes', exact: true }).click()
  const personalSlot = page.getByRole('group', { name: `${memberWarrior.alias} rune slots` })
    .getByRole('button', { name: new RegExp(`${memberWarrior.alias} rune slot 5`) })
  await personalSlot.click()
  await page.getByRole('dialog').getByRole('region', { name: 'Agency inventory' })
    .getByRole('button', { name: /Vitality Rune/ }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(personalSlot).toHaveAttribute('aria-label', /Vitality Rune/)
  await personalSlot.click()
  await page.getByRole('dialog').getByRole('button', { name: 'Unequip' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(personalSlot).toHaveAttribute('aria-label', /empty/)
  const agencySlot = page.getByRole('group', { name: 'Oakshield rune slots' })
    .getByRole('button', { name: /Oakshield rune slot 5/ })
  await agencySlot.click()
  await page.getByRole('dialog').getByRole('region', { name: 'Personal inventory' })
    .getByRole('button', { name: /Vitality Rune/ }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(agencySlot).toHaveAttribute('aria-label', /Vitality Rune/)
  await agencySlot.click()
  await page.getByRole('dialog').getByRole('button', { name: 'Unequip' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(agencySlot).toHaveAttribute('aria-label', /empty/)
  const transferred = await agencyState(page)
  expect(quantity(transferred.personalRuneInventory, 'vitality-rune')).toBe(1)
  expect(quantity(transferred.runeInventory, 'vitality-rune')).toBe(0)

  const session = await (await page.request.get('/api/v1/session')).json()
  let headers = { 'X-CSRF-TOKEN': session.csrfToken }
  const party = transferred.parties.find((candidate) => candidate.ownerManagerId === account.manager.id
    && candidate.name === 'Main Party')
  expect(party?.heroIds).toHaveLength(3)
  for (const heroId of party.heroIds.filter((id) => id !== memberWarrior.id)) {
    const removed = await page.request.delete(`/api/v1/agencies/${agencyId}/parties/${party.id}/heroes/${heroId}`, {
      headers,
    })
    expect(removed.status()).toBe(200)
  }
  const entry = await page.request.post('/api/v1/expeditions', { headers, data: {
    expeditionId: uuidV7(), commandId: uuidV7(), agencyId, partyId: party.id,
    mapId: '019c4c00-0006-7000-8000-000000000002',
  } })
  expect(entry.status()).toBe(201)
  const run = await entry.json()
  await page.reload()
  await page.getByRole('button', { name: 'Heroes', exact: true }).click()
  const refreshedSession = await (await page.request.get('/api/v1/session')).json()
  headers = { 'X-CSRF-TOKEN': refreshedSession.csrfToken }
  const awaySlot = page.getByRole('group', { name: `${memberWarrior.alias} rune slots` })
    .getByRole('button', { name: new RegExp(`${memberWarrior.alias} rune slot 5`) })
  await expect(awaySlot).toBeDisabled()
  const equip = (heroId) => page.request.put(`/api/v1/agencies/${agencyId}/heroes/${heroId}/rune-slots/4`, {
    headers,
    data: { operationKey: uuidV7(), runeId: vitalityRune.id, sourceOwnerType: 'MANAGER' },
    maxRedirects: 0,
  })
  expect((await equip(user1WarriorId)).status()).toBe(404)
  expect((await equip(memberWarrior.id)).status()).toBe(409)
  expect(quantity((await agencyState(page)).personalRuneInventory, 'vitality-rune')).toBe(1)
  const returned = await page.request.post(`/api/v1/expeditions/${run.expeditionId}/return`, { headers, data: { commandId: uuidV7(), expectedVersion: run.stateVersion } })
  expect(returned.status()).toBe(200)
  await expect.poll(async () => (await page.request.get('/api/v1/expeditions/active')).status(), { timeout: 120_000, intervals: [1000] }).toBe(204)

})
