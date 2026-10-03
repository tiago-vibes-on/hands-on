import { expect, test } from '@playwright/test'

test('Quest acceptance and cancellation survive a lost response and reload', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('user2@mail.com')
  await page.locator('#password').fill('user2')
  await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  const before = (await (await page.request.get('/api/v1/account')).json()).manager.gold
  let intent
  await page.route('**/api/v1/quests/*/accept', async (route) => {
    if (intent) return route.continue()
    const response = await route.fetch()
    expect(response.status()).toBe(200)
    intent = route.request().postDataJSON()
    await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Quest response lost after commit.' }) })
  })
  await page.getByRole('button', { name: 'Quests', exact: true }).click()
  const card = page.getByRole('region', { name: 'Quest board' }).locator('.quest-card').filter({ has: page.getByRole('heading', { name: 'Trolls at Broken Pass', exact: true }) })
  await card.getByRole('button', { name: 'Accept Quest' }).click()
  await expect(page.getByText('Quest response lost after commit.')).toBeVisible()
  expect(intent.commandId).toBeTruthy()
  const assignment = (await (await page.request.get('/api/v1/quests')).json()).activeAssignment
  expect(assignment.definition.title).toBe('Trolls at Broken Pass')
  await page.reload()
  await page.getByRole('button', { name: 'Quests', exact: true }).click()
  await page.getByRole('button', { name: 'Retry Quest command' }).click()
  await expect(page.getByRole('button', { name: 'Retry Quest command' })).toHaveCount(0)
  expect((await (await page.request.get('/api/v1/quests')).json()).activeAssignment.assignmentId).toBe(assignment.assignmentId)
  await page.getByRole('button', { name: 'Cancel Quest' }).click()
  await expect(page.getByRole('region', { name: 'Active Quest' })).toHaveCount(0)
  expect((await (await page.request.get('/api/v1/account')).json()).manager.gold).toBe(before)
})
