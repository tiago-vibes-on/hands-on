import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'

const appOrigin = 'https://app.e2e.heroassociation.test'
const authOrigin = 'https://auth.e2e.heroassociation.test'

test('registers, signs out, and signs back into an isolated account', async ({ page }) => {
  const email = `e2e-${randomUUID()}@example.test`
  const password = 'E2eAccount123!'

  await page.goto('/')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page).toHaveURL(new RegExp(`^${authOrigin}/realms/hero-association/`))
  await expect(page.locator('#kc-register-form')).toBeVisible()
  await page.locator('#email').fill(email)
  await page.locator('#password').fill(password)
  await page.locator('#password-confirm').fill(password)
  await page.getByRole('button', { name: 'Register', exact: true }).click()

  await expect(page).toHaveURL(`${appOrigin}/`)
  await expect(page.getByRole('heading', { name: 'Choose your manager name' })).toBeVisible()
  const firstAccount = await page.request.get(`${appOrigin}/api/v1/account`)
  expect(firstAccount.status()).toBe(200)
  const account = await firstAccount.json()
  expect(account).toMatchObject({ email, manager: null, agencyMemberships: [] })

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(`${appOrigin}/`)
  await expect(page.getByRole('heading', { name: 'Welcome to Hero Association' })).toBeVisible()

  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(new RegExp(`^${authOrigin}/realms/hero-association/`))
  await page.locator('#username').fill(email)
  await page.locator('#password').fill(password)
  await page.locator('#kc-login').click()

  await expect(page).toHaveURL(`${appOrigin}/`)
  await expect(page.getByRole('heading', { name: 'Choose your manager name' })).toBeVisible()
  const secondAccount = await page.request.get(`${appOrigin}/api/v1/account`)
  expect(secondAccount.status()).toBe(200)
  expect(await secondAccount.json()).toMatchObject({
    id: account.id,
    keycloakSubject: account.keycloakSubject,
    email,
    manager: null,
    agencyMemberships: [],
  })
})

test('refreshes an expired access token without losing the BFF session', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('user1@mail.com')
  await page.locator('#password').fill('user1')
  await page.locator('#kc-login').click()
  await expect(page).toHaveURL(`${appOrigin}/`)

  const first = await page.request.get('/api/v1/account', { maxRedirects: 0 })
  expect(first.status()).toBe(200)
  const firstAccount = await first.json()

  // The isolated realm issues thirty-second access tokens.
  await page.waitForTimeout(31_000)
  const refreshed = await page.request.get('/api/v1/account', { maxRedirects: 0 })
  expect(refreshed.status()).toBe(200)
  expect(await refreshed.json()).toMatchObject({
    id: firstAccount.id,
    keycloakSubject: firstAccount.keycloakSubject,
  })
})
