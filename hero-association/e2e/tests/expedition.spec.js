import { expect, test } from '@playwright/test'

test('User2 default party enters Troll Field, reconnects, continues, and settles after returning', async ({ page }) => {
  test.setTimeout(180_000)

  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('user2@mail.com')
  await page.locator('#password').fill('user2')
  await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()

  const previous = await page.request.get('/api/v1/expeditions/active')
  if (previous.status() === 200) {
    const active = await previous.json()
    await page.getByRole('button', { name: 'Map' }).click()
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
  expect(warrior).toBeTruthy()

  const party = state.parties.find((candidate) => candidate.ownerManagerId === managerId
    && candidate.name === 'Main Party')
  expect(party?.heroIds).toHaveLength(3)
  expect(party.heroIds).toEqual(expect.arrayContaining(heroes.map((hero) => hero.id)))

  await page.reload()
  await expect(page.getByRole('button', { name: 'Map' })).toBeVisible()
  await page.getByRole('button', { name: 'Map' }).click()
  await expect(page.getByRole('heading', { name: 'Troll Field' })).toBeVisible()
  await page.getByRole('combobox', { name: 'Party' }).selectOption(party.id)
  const webSocketOpened = page.waitForEvent('websocket', (socket) => socket.url().includes('/ws/v1/expeditions/'))
  await page.getByRole('button', { name: 'Enter field' }).click()
  await webSocketOpened
  await expect(page.getByRole('heading', { name: 'In battle' })).toBeVisible()
  await expect(page.locator('.combat-scene canvas')).toBeVisible()
  await expect(page.locator('.map-controls__connection')).toHaveText('Live')
  await expect(page.locator('.map-fight__time')).toHaveText(/[1-9][0-9]*s/, { timeout: 20_000 })

  const active = await (await page.request.get('/api/v1/expeditions/active')).json()
  expect(active.ownerManagerId).toBe(managerId)
  expect(active.partyId).toBe(party.id)

  const beforeLeaving = active.fight.visual.elapsedMilliseconds
  await page.getByRole('button', { name: 'Overview' }).click()
  await expect.poll(async () => {
    const current = await (await page.request.get('/api/v1/expeditions/active')).json()
    return current.fight?.visual?.elapsedMilliseconds ?? 0
  }, { timeout: 15_000, intervals: [1_000] }).toBeGreaterThan(beforeLeaving + 3_000)
  await page.getByRole('button', { name: 'Map' }).click()
  await expect(page.getByRole('heading', { name: 'In battle' })).toBeVisible()
  await expect(page.locator('.combat-scene canvas')).toBeVisible()
  const resumedSeconds = Number.parseInt(await page.locator('.map-fight__time').textContent(), 10)
  expect(resumedSeconds).toBeGreaterThanOrEqual(Math.floor(beforeLeaving / 1_000) + 3)

  await page.reload()
  await page.getByRole('button', { name: 'Map' }).click()
  await expect(page.getByRole('heading', { name: 'In battle' })).toBeVisible()
  await expect(page.locator('.combat-scene canvas')).toBeVisible()
  await expect(page.locator('.map-fight__time')).toHaveText(/[1-9][0-9]*s/, { timeout: 20_000 })

  await expect(page.getByRole('heading', { name: 'Encounter complete' })).toBeVisible({ timeout: 90_000 })
  await page.getByRole('button', { name: 'Continue', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'In battle' })).toBeVisible()
  await page.getByRole('button', { name: 'Return after this fight' }).click()
  await expect(page.getByText('Return requested; this fight will finish first.')).toBeVisible()

  // No viewer remains, but the worker must finish and settle the second fight.
  await page.getByRole('button', { name: 'Overview' }).click()
  await expect.poll(async () => (await page.request.get('/api/v1/expeditions/active')).status(), {
    timeout: 120_000,
    intervals: [1_000],
  }).toBe(204)

  const settled = await (await page.request.get(`/api/v1/agencies/${agencyId}/state`)).json()
  expect(settled.personalHeroes.find((hero) => hero.id === warrior.id).experience)
    .toBeGreaterThan(warrior.experience)
})
