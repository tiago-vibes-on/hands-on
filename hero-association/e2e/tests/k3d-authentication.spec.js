import { expect, test } from '@playwright/test'

const appUrl = 'https://heroassociation.test:8443/'
const authUrl = /https:\/\/auth\.heroassociation\.test:8443\/realms\/hero-association\//
const dawnwatchAgencyId = '019c4c00-0001-7000-8000-000000000001'

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
  return agencyId
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

test('an authenticated session keeps serving game state across repeated requests', async ({ page }) => {
  const agencyId = await signIn(page, 'user1@mail.com', 'user1', 'User 1')
  for (let request = 0; request < 40; request += 1) {
    const response = await page.request.get(`/api/v1/agencies/${agencyId}/state`, { maxRedirects: 0 })
    expect(response.status()).toBe(200)
    expect((await response.json()).agency.id).toBe(agencyId)
    await response.dispose()
  }
  await expect(page.locator('.player-card strong')).toHaveText('User 1')
})

test('user2 cannot read user1 agency through either service', async ({ page }) => {
  const ownAgencyId = await signIn(page, 'user2@mail.com', 'user2', 'User 2')
  expect(ownAgencyId).not.toBe(dawnwatchAgencyId)
  const response = await page.request.get(`/api/v1/agencies/${dawnwatchAgencyId}/state`, { maxRedirects: 0 })
  expect(response.status()).toBe(403)
  await response.dispose()
})
