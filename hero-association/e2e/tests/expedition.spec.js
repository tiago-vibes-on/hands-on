import { expect, test } from '@playwright/test'

test('User2 default party enters Troll Field, reconnects, continues, and settles after returning', async ({ page }) => {
  test.setTimeout(450_000)

  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('user2@mail.com')
  await page.locator('#password').fill('user2')
  await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()

  const previous = await page.request.get('/api/v1/expeditions/active')
  if (previous.status() === 200) {
    const active = await previous.json()
    await page.getByRole('button', { name: 'Map', exact: true }).click()
    if (!active.returnRequested && active.phase !== 'SETTLEMENT_PENDING') {
      await page.getByRole('button', {
        name: active.phase === 'FIGHTING' ? 'Return after this fight' : 'Return to agency',
      }).click()
    }
    await expect.poll(async () => (await page.request.get('/api/v1/expeditions/active')).status(), {
      timeout: 120_000,
      intervals: [1_000],
    }).toBe(204)
    await page.getByRole('button', { name: 'Overview' }).click()
  } else {
    expect(previous.status()).toBe(204)
  }

  const account = await (await page.request.get('/api/v1/account')).json()
  const agencyId = account.agencyMemberships[0].agencyId
  const managerId = account.manager.id
  const state = await (await page.request.get(`/api/v1/agencies/${agencyId}/state`)).json()
  const heroes = state.personalHeroes.filter((hero) => hero.ownerManagerId === managerId)
  expect(heroes).toHaveLength(3)
  const warrior = heroes.find((hero) => hero.heroClass === 'WARRIOR')
  const mage = heroes.find((hero) => hero.heroClass === 'MAGE')
  expect(warrior).toBeTruthy()
  expect(mage?.magicLevel).toBeGreaterThanOrEqual(15)
  for (const hero of heroes) {
    expect(hero.runeSlots.filter((slot) => slot.rune)).toHaveLength(2)
  }

  const party = state.parties.find((candidate) => candidate.ownerManagerId === managerId
    && candidate.name === 'Main Party')
  expect(party?.heroIds).toHaveLength(3)
  expect(party.heroIds).toEqual(expect.arrayContaining(heroes.map((hero) => hero.id)))

  await page.addInitScript(() => {
    const NativeWebSocket = window.WebSocket
    window.WebSocket = class extends NativeWebSocket {
      constructor(url, protocols) {
        super(url, protocols)
        if (String(url).includes('/ws/v1/expeditions/')) {
          window.expeditionTestSocket = this
        }
      }
    }
  })
  await page.reload()
  await expect(page.getByRole('button', { name: 'Map', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Map', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Troll Field' })).toBeVisible()
  await page.getByRole('combobox', { name: 'Party' }).selectOption(party.id)
  let lostEntry
  await page.route('**/api/v1/expeditions', async (route) => {
    if (route.request().method() !== 'POST' || lostEntry) return route.continue()
    const response = await route.fetch()
    expect(response.status()).toBe(201)
    lostEntry = route.request().postDataJSON()
    await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Entry response lost after admission.' }) })
  })
  const webSocketOpened = page.waitForEvent('websocket', (socket) => socket.url().includes('/ws/v1/expeditions/'))
  await page.getByRole('button', { name: 'Enter field' }).click()
  await expect(page.getByText('Entry response lost after admission.')).toBeVisible()
  expect(lostEntry.expeditionId).toBeTruthy()
  await page.reload()
  await page.getByRole('button', { name: 'Map', exact: true }).click()
  await webSocketOpened
  await expect.poll(() => page.evaluate((manager) => sessionStorage.getItem(`hero-association:expedition-entry:${manager}`), managerId)).toBeNull()
  await expect(page.getByRole('heading', { name: 'In battle' })).toBeVisible()
  await expect(page.locator('.combat-scene canvas')).toBeVisible()
  const loadout = page.getByRole('region', { name: 'Party loadout' })
  await expect(loadout.getByText('Fire Ball')).toBeVisible()
  await expect(loadout.getByText('Lightning Rail')).toBeVisible()
  await expect(loadout.getByText(/Magic Level 10/)).toBeVisible()
  await expect(loadout.getByText(/Magic Level 15/)).toBeVisible()
  await expect(loadout.getByText('Mana Rune')).toBeVisible()
  await expect(loadout.getByText('Attack Rune')).toBeVisible()
  const progress = page.getByRole('region', { name: 'Expedition progress' })
  await expect(progress.getByText('Carried gold')).toBeVisible()
  await expect(progress.getByText('Items', { exact: true })).toBeVisible()
  await expect(progress.locator('article').first()).toContainText('Stamina')
  await expect(progress.locator('article').first()).toContainText('Melee')
  await expect(page.locator('.map-controls__connection')).toHaveText('Live')
  await expect(page.locator('.map-fight__time')).toHaveText(/[1-9][0-9]*s/, { timeout: 20_000 })

  const active = await (await page.request.get('/api/v1/expeditions/active')).json()
  expect(active.ownerManagerId).toBe(managerId)
  expect(active.partyId).toBe(party.id)
  expect(active.fight.visual.creatures).toHaveLength(3)
  expect(new Set(active.fight.visual.creatures.map((creature) => creature.id)).size).toBe(3)

  await page.setViewportSize({ width: 390, height: 844 })
  await expect.poll(() => page.locator('.map-fight__canvas-scroll').evaluate(
    (scrollArea) => scrollArea.scrollWidth > scrollArea.clientWidth)).toBe(true)
  await page.setViewportSize({ width: 1280, height: 800 })

  await page.evaluate(() => window.expeditionTestSocket.close(4001, 'Reconnect test'))
  await expect(page.locator('.map-controls__connection')).toHaveText('Reconnecting to battle…', {
    timeout: 15_000,
  })
  await expect(page.locator('.map-controls__connection')).toHaveText('Live', { timeout: 30_000 })

  const beforeLeaving = active.fight.visual.elapsedMilliseconds
  await page.getByRole('button', { name: 'Overview' }).click()
  await expect.poll(async () => {
    const current = await (await page.request.get('/api/v1/expeditions/active')).json()
    return current.fight?.visual?.elapsedMilliseconds ?? 0
  }, { timeout: 15_000, intervals: [1_000] }).toBeGreaterThan(beforeLeaving + 3_000)
  await page.getByRole('button', { name: 'Map', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'In battle' })).toBeVisible()
  await expect(page.locator('.combat-scene canvas')).toBeVisible()
  const resumedSeconds = Number.parseInt(await page.locator('.map-fight__time').textContent(), 10)
  expect(resumedSeconds).toBeGreaterThanOrEqual(Math.floor(beforeLeaving / 1_000) + 3)

  await page.reload()
  await page.getByRole('button', { name: 'Map', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'In battle' })).toBeVisible()
  await expect(page.locator('.combat-scene canvas')).toBeVisible()
  await expect(page.locator('.map-fight__time')).toHaveText(/[1-9][0-9]*s/, { timeout: 20_000 })

  await expect(page.getByRole('heading', { name: 'Encounter complete' })).toBeVisible({ timeout: 150_000 })
  const autoContinue = page.getByRole('checkbox', { name: 'Auto-continue' })
  await expect(autoContinue).not.toBeChecked()
  await page.waitForTimeout(2_000)
  const waiting = await (await page.request.get('/api/v1/expeditions/active')).json()
  expect(waiting.phase).toBe('AWAITING_CONTINUE')
  expect(waiting.encounterIndex).toBe(1)
  await autoContinue.check()
  await expect(page.getByRole('heading', { name: 'In battle' })).toBeVisible({ timeout: 15_000 })
  await expect.poll(async () => (await (await page.request.get('/api/v1/expeditions/active')).json()).encounterIndex)
    .toBe(2)
  await autoContinue.uncheck()
  await expect(page.getByRole('heading', { name: 'Encounter complete' })).toBeVisible({ timeout: 150_000 })
  const secondResult = await (await page.request.get('/api/v1/expeditions/active')).json()
  expect(secondResult.phase).toBe('AWAITING_CONTINUE')
  expect(secondResult.heroes.find((hero) => hero.heroId === warrior.id).experience)
    .toBeGreaterThan(warrior.experience)
  await expect(progress.locator('article').filter({ has: page.getByRole('heading', { name: warrior.name }) }))
    .toContainText(`XP ${secondResult.heroes.find((hero) => hero.heroId === warrior.id).experience}`)
  await page.getByRole('button', { name: 'Continue', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'In battle' })).toBeVisible()
  await page.getByRole('button', { name: 'Return after this fight' }).click()
  await expect(page.getByText('Return requested; this fight will finish first.')).toBeVisible()

  // No viewer remains, but the worker must finish and settle the third fight.
  await page.getByRole('button', { name: 'Overview' }).click()
  await expect.poll(async () => (await page.request.get('/api/v1/expeditions/active')).status(), {
    timeout: 150_000,
    intervals: [1_000],
  }).toBe(204)

  const settled = await (await page.request.get(`/api/v1/agencies/${agencyId}/state`)).json()
  expect(settled.personalHeroes.find((hero) => hero.id === warrior.id).experience)
    .toBeGreaterThanOrEqual(secondResult.heroes.find((hero) => hero.heroId === warrior.id).experience)
})
