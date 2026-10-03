import { expect, test } from '@playwright/test'
import { readFile, writeFile } from 'node:fs/promises'
function newUuidV7() {
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


const origin = 'https://app.e2e.heroassociation.test'
const statePath = '/session/world-quest-session.json'
const fixturePath = '/session/world-quest.json'
async function signIn(page, email, password) {
  await page.goto(origin)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill(email); await page.locator('#password').fill(password); await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
}
async function headers(page) {
  const session = await (await page.request.get(`${origin}/api/v1/session`)).json()
  return { 'X-CSRF-TOKEN': session.csrfToken }
}
async function resumed(browser, use) {
  const context = await browser.newContext({ storageState: statePath, ignoreHTTPSErrors: true })
  const page = await context.newPage()
  try { await use(page, JSON.parse(await readFile(fixturePath, 'utf8'))); await context.storageState({ path: statePath }) }
  finally { await context.close() }
}
async function active(page) { const result = await page.request.get(`${origin}/api/v1/expeditions/active`); expect(result.status()).toBe(200); return result.json() }

test('pins a dungeon and completion Quest before dependency outages', async ({ page }) => {
  test.setTimeout(240_000)
  await signIn(page, 'user2@mail.com', 'user2')
  const account = await (await page.request.get('/api/v1/account')).json()
  const agencyId = account.agencyMemberships[0].agencyId
  const agency = await (await page.request.get(`/api/v1/agencies/${agencyId}/state`)).json()
  const party = agency.parties.find((p) => p.ownerManagerId === account.manager.id && p.name === 'Main Party')
  const map = (await (await page.request.get('/api/v1/maps')).json()).find((m) => m.kind === 'DUNGEON')
  const board = await (await page.request.get('/api/v1/quests')).json()
  expect(board.activeAssignment).toBeNull()
  const quest = board.definitions.find((q) => q.objective === 'DUNGEON_COMPLETION')
  const accepted = await page.request.post(`/api/v1/quests/${quest.definitionId}/accept`, { headers: await headers(page), data: { commandId: newUuidV7() } })
  expect(accepted.status()).toBe(200)
  const assignment = await accepted.json()
  await page.reload(); await page.getByRole('button', { name: 'Map', exact: true }).click()
  await page.getByLabel('Destination', { exact: true }).selectOption(map.definitionId)
  await page.getByRole('button', { name: 'Enter dungeon' }).click()
  await expect(page.getByRole('region', { name: 'Current encounter' })).toContainText(map.name)
  const run = await active(page)
  expect(run.map.version).toBe(map.version); expect(run.quest.pin.assignment.assignmentId).toBe(assignment.assignmentId)
  await writeFile(fixturePath, JSON.stringify({ agencyId, heroes: party.heroIds, runId: run.expeditionId, mapId: map.definitionId, partyId: party.id, assignmentId: assignment.assignmentId, beforeGold: account.manager.gold, beforeItems: account.manager.items.find((row) => row.code === 'iron-ingot')?.quantity ?? 0 }))
  await page.context().storageState({ path: statePath })
})

test('World outage preserves pinned floors and rejects new admission', async ({ browser }) => {
  test.setTimeout(300_000)
  await resumed(browser, async (page, fixture) => {
    // The BFF emits 502 for a connection failure; its sidecar can report 503 first.
    expect([502, 503]).toContain((await page.request.get(`${origin}/api/v1/maps`)).status())
    await expect.poll(async () => (await active(page)).phase, { timeout: 150_000, intervals: [1000] }).toBe('AWAITING_CONTINUE')
    const first = await active(page)
    expect(first.floor).toBe(1)
    const continued = await page.request.post(`${origin}/api/v1/expeditions/${fixture.runId}/continue`, { headers: await headers(page), data: { commandId: newUuidV7(), expectedVersion: first.stateVersion } })
    expect(continued.status()).toBe(200); expect((await continued.json()).floor).toBe(2)
    await expect.poll(async () => (await active(page)).phase, { timeout: 150_000, intervals: [1000] }).toBe('DUNGEON_COMPLETED')
    const complete = await active(page)
    expect(complete.quest.progress).toBe(1)
    expect((await page.request.post(`${origin}/api/v1/expeditions/${fixture.runId}/continue`, { headers: await headers(page), data: { commandId: newUuidV7(), expectedVersion: complete.stateVersion } })).status()).toBe(409)
    const other = await browser.newContext({ ignoreHTTPSErrors: true }); const newcomer = await other.newPage()
    try {
      await signIn(newcomer, 'manager10@mail.com', 'manager10')
      const account = await (await newcomer.request.get('/api/v1/account')).json()
      const agency = await (await newcomer.request.get(`/api/v1/agencies/${account.agencyMemberships[0].agencyId}/state`)).json()
      const party = agency.parties.find((p) => p.ownerManagerId === account.manager.id && p.name === 'Main Party')
      const rejected = await newcomer.request.post('/api/v1/expeditions', { headers: await headers(newcomer), data: { expeditionId: newUuidV7(), commandId: newUuidV7(), agencyId: agency.agency.id, partyId: party.id, mapId: fixture.mapId } })
      expect(rejected.status()).toBe(503)
      expect((await newcomer.request.get('/api/v1/expeditions/active')).status()).toBe(204)
      const after = await (await newcomer.request.get(`/api/v1/agencies/${agency.agency.id}/state`)).json()
      expect(after.personalHeroes.filter((hero) => hero.ownerManagerId === account.manager.id).every((hero) => hero.activity !== 'ON_EXPEDITION')).toBe(true)
    } finally { await other.close() }
  })
})

test('Quest reward outage retains Expedition and Hero fences', async ({ browser }) => {
  test.setTimeout(90_000)
  await resumed(browser, async (page, fixture) => {
    const run = await active(page)
    const returned = await page.request.post(`${origin}/api/v1/expeditions/${fixture.runId}/return`, { headers: await headers(page), data: { commandId: newUuidV7(), expectedVersion: run.stateVersion } })
    expect(returned.status()).toBe(200)
    await expect.poll(async () => (await (await page.request.get(`${origin}/api/v1/quests`)).json()).activeAssignment.status, { timeout: 30_000 }).toBe('REWARD_PENDING')
    expect((await active(page)).phase).toBe('SETTLEMENT_PENDING')
    // Account needs Assets; the authoritative Quest admission retains the Manager claim.
    expect((await (await page.request.get(`${origin}/api/v1/quests`)).json()).atAgency).toBe(false)
  })
})

test('Quest restart recovery credits the completed dungeon reward exactly once', async ({ browser }) => {
  test.setTimeout(180_000)
  await resumed(browser, async (page, fixture) => {
    await expect.poll(async () => (await page.request.get(`${origin}/api/v1/expeditions/active`)).status(), { timeout: 120_000, intervals: [1000] }).toBe(204)
    const board = await (await page.request.get(`${origin}/api/v1/quests`)).json()
    expect(board.activeAssignment).toBeNull(); expect(board.atAgency).toBe(true)
    expect(board.recentAssignments.find((q) => q.assignmentId === fixture.assignmentId).status).toBe('COMPLETED')
    const after = (await (await page.request.get(`${origin}/api/v1/account`)).json()).manager
    expect(after.gold).toBe(fixture.beforeGold + 160)
    expect(after.items.find((row) => row.code === 'iron-ingot').quantity).toBe(fixture.beforeItems + 1)
    const agency = await (await page.request.get(`${origin}/api/v1/agencies/${fixture.agencyId}/state`)).json()
    expect(agency.personalHeroes.filter((hero) => fixture.heroes.includes(hero.id)).every((hero) => hero.activity !== 'ON_EXPEDITION')).toBe(true)
    await page.waitForTimeout(3500)
    expect((await (await page.request.get(`${origin}/api/v1/account`)).json()).manager.gold).toBe(after.gold)
  })
})
