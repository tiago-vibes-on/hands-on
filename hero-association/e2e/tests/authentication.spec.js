import { expect, test } from '@playwright/test'

test('signs in and fully signs out of the Keycloak session', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Welcome to Hero Association' })).toBeVisible()
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page).toHaveURL(/http:\/\/host\.docker\.internal:18180\/realms\/hero-association\//)
  await page.locator('#username').fill('user1@mail.com')
  await page.locator('#password').fill('user1')
  await page.locator('#kc-login').click()

  await expect(page).toHaveURL('http://host.docker.internal:15173/')
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  await expect(page.getByText('Dawnwatch Agency', { exact: true }).first()).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).click()

  await expect(page).toHaveURL('http://host.docker.internal:15173/')
  await expect(page.getByRole('heading', { name: 'Welcome to Hero Association' })).toBeVisible()

  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/http:\/\/host\.docker\.internal:18180\/realms\/hero-association\//)
  await expect(page.locator('#kc-form-login')).toBeVisible()
})
