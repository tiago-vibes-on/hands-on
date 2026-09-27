import { expect, test } from '@playwright/test'

const appUrl = 'https://k3d.heroassociation.test:19443/'
const authUrl = /https:\/\/auth\.k3d\.heroassociation\.test:19443\/realms\/hero-association\//

async function signIn(page, email, password, managerName) {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Welcome to Hero Association' })).toBeVisible()
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(authUrl)
  await page.locator('#username').fill(email)
  await page.locator('#password').fill(password)
  await page.locator('#kc-login').click()
  await expect(page).toHaveURL(appUrl)
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  await expect(page.locator('.player-card strong')).toHaveText(managerName)

  const sessionResponse = await page.request.get('/api/v1/session')
  expect(sessionResponse.ok()).toBeTruthy()
  expect((await sessionResponse.json()).authenticated).toBe(true)

  const accountResponse = await page.request.get('/api/v1/account')
  expect(accountResponse.ok()).toBeTruthy()
  const account = await accountResponse.json()
  expect(account.manager.displayName).toBe(managerName)
  const agencyId = account.agencyMemberships[0].agencyId
  const agencyResponse = await page.request.get(`/api/v1/agencies/${agencyId}/state`)
  expect(agencyResponse.ok()).toBeTruthy()
  expect((await agencyResponse.json()).agency.id).toBe(agencyId)
}

test('user1 signs in, signs out, and signs in again through k3d', async ({ page }) => {
  await signIn(page, 'user1@mail.com', 'user1', 'User 1')
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(appUrl)
  await expect(page.getByRole('heading', { name: 'Welcome to Hero Association' })).toBeVisible()
  expect((await (await page.request.get('/api/v1/session')).json()).authenticated).toBe(false)

  await signIn(page, 'user1@mail.com', 'user1', 'User 1')
})

test('user2 sees their own manager account through k3d', async ({ page }) => {
  await signIn(page, 'user2@mail.com', 'user2', 'User 2')
})
