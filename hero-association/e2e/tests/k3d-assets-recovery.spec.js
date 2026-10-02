import { expect, test } from '@playwright/test'
import { readFile, writeFile } from 'node:fs/promises'

const origin = 'https://app.e2e.heroassociation.test'
const directory = '/session'
const agencyId = '019c4c00-0001-7000-8000-000000000001'
const fixturePath = `${directory}/assets-operation.json`
const statePath = `${directory}/assets-session.json`

function operationKey() {
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  for (let index = 0; index < 6; index++) bytes[index] = Math.floor(Date.now() / 2 ** (8 * (5 - index))) & 0xff
  bytes[6] = (bytes[6] & 0x0f) | 0x70
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

test('prepares an equipment operation before Assets outage', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('user1@mail.com')
  await page.locator('#password').fill('user1')
  await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  const state = await (await page.request.get(`/api/v1/agencies/${agencyId}/state`)).json()
  const hero = state.personalHeroes.find((candidate) => candidate.heroClass === 'MAGE'
    && ['TRAINING', 'RESTING'].includes(candidate.activity))
  const sourceHero = state.personalHeroes.find((candidate) => candidate.id !== hero?.id
    && ['TRAINING', 'RESTING'].includes(candidate.activity) && candidate.runeSlots.some((slot) => slot.rune))
  expect(hero).toBeTruthy()
  expect(sourceHero).toBeTruthy()
  const sourceSlot = sourceHero.runeSlots.find((slot) => slot.rune)
  const session = await (await page.request.get('/api/v1/session')).json()
  const released = await page.request.delete(`/api/v1/agencies/${agencyId}/heroes/${sourceHero.id}/rune-slots/${sourceSlot.slot}`, {
    headers: { 'X-CSRF-TOKEN': session.csrfToken, 'X-Operation-Key': operationKey() },
  })
  expect(released.status()).toBe(200)
  const stack = (await released.json()).personalRuneInventory.find((entry) => entry.rune.id === sourceSlot.rune.id)
  expect(stack.quantity).toBeGreaterThan(0)
  const slotIndex = hero.runeSlots.findIndex((slot) => !slot.rune)
  expect(slotIndex).toBeGreaterThanOrEqual(0)
  await writeFile(fixturePath, JSON.stringify({
    path: `/api/v1/agencies/${agencyId}/heroes/${hero.id}/rune-slots/${slotIndex}`,
    request: { operationKey: operationKey(), runeId: stack.rune.id, sourceOwnerType: 'MANAGER' },
    heroId: hero.id, slotIndex, initialQuantity: stack.quantity,
    agencyGold: state.agency.gold,
  }), { mode: 0o600 })
  await page.context().storageState({ path: statePath })
})

test('Assets outage leaves a durable pending operation and fences the Hero', async ({ browser }) => {
  const fixture = JSON.parse(await readFile(fixturePath, 'utf8'))
  const context = await browser.newContext({ storageState: statePath, ignoreHTTPSErrors: true })
  try {
    const page = await context.newPage()
    const session = await (await page.request.get(`${origin}/api/v1/session`)).json()
    const headers = { 'X-CSRF-TOKEN': session.csrfToken }
    const result = await page.request.put(origin + fixture.path, { headers, data: fixture.request })
    expect(result.status()).toBe(202)
    expect(await result.json()).toMatchObject({ operationKey: fixture.request.operationKey, status: 'PENDING' })
    const retry = await page.request.put(origin + fixture.path, { headers, data: fixture.request })
    expect(retry.status()).toBe(202)
    const conflicting = await page.request.put(origin + fixture.path, {
      headers, data: { ...fixture.request, operationKey: operationKey() },
    })
    expect(conflicting.status()).toBe(409)
    const status = await page.request.get(`${origin}/api/v1/asset-operations/${fixture.request.operationKey}`)
    expect((await status.json()).status).toBe('PENDING')
  } finally { await context.close() }
})

test('Core restart and Assets recovery complete the original operation once', async ({ browser }) => {
  const fixture = JSON.parse(await readFile(fixturePath, 'utf8'))
  const context = await browser.newContext({ storageState: statePath, ignoreHTTPSErrors: true })
  try {
    const page = await context.newPage()
    await expect.poll(async () => {
      const response = await page.request.get(`${origin}/api/v1/asset-operations/${fixture.request.operationKey}`)
      return response.status() === 200 ? (await response.json()).status : `HTTP ${response.status()}`
    }, { timeout: 120_000 }).toBe('APPLIED')
    const session = await (await page.request.get(`${origin}/api/v1/session`)).json()
    const response = await page.request.put(origin + fixture.path, {
      headers: { 'X-CSRF-TOKEN': session.csrfToken }, data: fixture.request,
    })
    expect(response.status()).toBe(200)
    const state = await response.json()
    expect(state.personalHeroes.find((hero) => hero.id === fixture.heroId)
      .runeSlots[fixture.slotIndex].rune.id).toBe(fixture.request.runeId)
    expect(state.personalRuneInventory.find((entry) => entry.rune.id === fixture.request.runeId).quantity)
      .toBe(fixture.initialQuantity - 1)
    expect(state.agency.gold).toBe(fixture.agencyGold)
  } finally { await context.close() }
})
