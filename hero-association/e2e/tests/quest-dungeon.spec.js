import { expect, test } from '@playwright/test'

function uuid() {
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  for (let index = 0; index < 6; index++) bytes[index] = Math.floor(Date.now() / 2 ** (8 * (5 - index))) & 0xff
  bytes[6] = (bytes[6] & 0x0f) | 0x70; bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

test('clearing the cavern returns one Quest reward and restores the Party', async ({ page }) => {
  test.setTimeout(300_000)
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('user2@mail.com'); await page.locator('#password').fill('user2'); await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  const before = (await (await page.request.get('/api/v1/account')).json()).manager
  const oreBefore = before.items.find((row) => row.code === 'iron-ingot')?.quantity ?? 0
  await page.getByRole('button', { name: 'Quests', exact: true }).click()
  const card = page.getByRole('region', { name: 'Quest board' }).locator('.quest-card').filter({ has: page.getByRole('heading', { name: 'Clear Broken Pass Cavern', exact: true }) })
  await card.getByRole('button', { name: 'Accept Quest' }).click()
  await expect(page.getByRole('region', { name: 'Active Quest' })).toContainText('Clear Broken Pass Cavern')
  const assignment = (await (await page.request.get('/api/v1/quests')).json()).activeAssignment
  await page.getByRole('button', { name: 'Map', exact: true }).click()
  const maps = await (await page.request.get('/api/v1/maps')).json()
  await page.getByLabel('Destination', { exact: true }).selectOption(maps.find((map) => map.kind === 'DUNGEON').definitionId)
  await page.getByRole('button', { name: 'Enter dungeon' }).click()
  await expect(page.getByRole('heading', { name: 'Encounter complete' })).toBeVisible({ timeout: 120_000 })
  await page.getByRole('button', { name: 'Continue', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Dungeon complete' })).toBeVisible({ timeout: 120_000 })
  await expect(page.getByRole('button', { name: 'Continue', exact: true })).toHaveCount(0)
  await expect(page.getByLabel('Auto-continue')).toBeDisabled()
  await expect(page.getByRole('region', { name: 'Expedition progress' })).toContainText('1 / 1 · saved on return')
  await page.getByRole('button', { name: 'Return to agency', exact: true }).click()
  await expect.poll(async () => (await page.request.get('/api/v1/expeditions/active')).status(), { timeout: 120_000, intervals: [1000] }).toBe(204)
  const after = (await (await page.request.get('/api/v1/account')).json()).manager
  expect(after.gold).toBe(before.gold + 160)
  expect(after.items.find((row) => row.code === 'iron-ingot').quantity).toBe(oreBefore + 1)
  const board = await (await page.request.get('/api/v1/quests')).json()
  expect(board.activeAssignment).toBeNull(); expect(board.recentAssignments.find((entry) => entry.assignmentId === assignment.assignmentId).status).toBe('COMPLETED')
  await page.waitForTimeout(2500)
  expect((await (await page.request.get('/api/v1/account')).json()).manager.gold).toBe(after.gold)
})
