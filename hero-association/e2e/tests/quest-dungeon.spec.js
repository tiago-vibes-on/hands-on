import { expect, test } from '@playwright/test'
import { cavernReward, expectReturnedLoot } from '../loot-return.js'

async function expectCarriedLoot(page, carried) {
  const progress = page.getByRole('region', { name: 'Expedition progress' })
  const counters = [
    ['Carried gold', carried.gold],
    ['Items', Object.values(carried.items).reduce((sum, quantity) => sum + quantity, 0)],
    ['Runes', Object.values(carried.runes).reduce((sum, quantity) => sum + quantity, 0)],
  ]
  for (const [label, quantity] of counters) {
    await expect(progress.locator('.map-progress__carried > div').filter({ has: page.getByText(label, { exact: true }) }).locator('strong')).toHaveText(String(quantity))
  }
}

test('auto-continue repeats a cleared dungeon and Return banks all loot with one Quest reward', async ({ page }) => {
  test.setTimeout(360_000)
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('user2@mail.com'); await page.locator('#password').fill('user2'); await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  const before = (await (await page.request.get('/api/v1/account')).json()).manager
  await page.getByRole('button', { name: 'Quests', exact: true }).click()
  const card = page.getByRole('region', { name: 'Quest board' }).locator('.quest-card').filter({ has: page.getByRole('heading', { name: 'Clear Broken Pass Cavern', exact: true }) })
  await card.getByRole('button', { name: 'Accept Quest' }).click()
  await expect(page.getByRole('region', { name: 'Active Quest' })).toContainText('Clear Broken Pass Cavern')
  const assignment = (await (await page.request.get('/api/v1/quests')).json()).activeAssignment
  await page.getByRole('button', { name: 'Map', exact: true }).click()
  const maps = await (await page.request.get('/api/v1/maps')).json()
  await page.getByRole('combobox', { name: 'Destination', exact: true }).selectOption(maps.find((map) => map.kind === 'DUNGEON').definitionId)
  await page.getByRole('button', { name: 'Enter dungeon' }).click()
  await expect(page.getByRole('heading', { name: 'Encounter complete' })).toBeVisible({ timeout: 120_000 })
  await page.getByRole('button', { name: 'Continue', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Dungeon complete' })).toBeVisible({ timeout: 120_000 })
  await expect(page.getByRole('button', { name: 'Continue', exact: true })).toHaveCount(0)
  await expect(page.getByLabel('Auto-continue')).toBeEnabled()
  await expect(page.getByLabel('Auto-continue')).not.toBeChecked()
  await expect(page.getByRole('region', { name: 'Expedition progress' })).toContainText('1 / 1 · saved on return')
  const completed = await (await page.request.get('/api/v1/expeditions/active')).json()
  expect(completed.canContinue).toBe(true)
  await expectCarriedLoot(page, completed.carried)
  expectReturnedLoot(before, (await (await page.request.get('/api/v1/account')).json()).manager, { gold: 0, items: {}, runes: {} })
  const nextResponse = page.waitForResponse((response) => response.request().method() === 'POST'
    && response.url().endsWith(`/api/v1/expeditions/${completed.expeditionId}/continue`))
  await page.getByLabel('Auto-continue').check()
  const restarted = await nextResponse
  expect(restarted.status()).toBe(200)
  const repeated = await restarted.json()
  expect(repeated.expeditionId).toBe(completed.expeditionId)
  expect(repeated.phase).toBe('FIGHTING'); expect(repeated.floor).toBe(1)
  expect(repeated.stateVersion).toBeGreaterThan(completed.stateVersion)
  expect(repeated.map).toEqual(completed.map); expect(repeated.heroes).toEqual(completed.heroes)
  expect(repeated.quest).toEqual(completed.quest); expect(repeated.carried).toEqual(completed.carried)
  await page.getByLabel('Auto-continue').uncheck()
  await expect(page.getByRole('heading', { name: 'Encounter complete' })).toBeVisible({ timeout: 120_000 })
  const returning = await (await page.request.get('/api/v1/expeditions/active')).json()
  expect(returning.phase).toBe('AWAITING_CONTINUE'); expect(returning.quest.progress).toBe(1)
  await expectCarriedLoot(page, returning.carried)
  await page.getByRole('button', { name: 'Return to agency', exact: true }).click()
  await expect.poll(async () => (await page.request.get('/api/v1/expeditions/active')).status(), { timeout: 120_000, intervals: [1000] }).toBe(204)
  const after = (await (await page.request.get('/api/v1/account')).json()).manager
  expectReturnedLoot(before, after, returning.carried, cavernReward)
  const board = await (await page.request.get('/api/v1/quests')).json()
  expect(board.activeAssignment).toBeNull(); expect(board.recentAssignments.find((entry) => entry.assignmentId === assignment.assignmentId).status).toBe('COMPLETED')
  await page.waitForTimeout(2500)
  expectReturnedLoot(after, (await (await page.request.get('/api/v1/account')).json()).manager, { gold: 0, items: {}, runes: {} })
})
