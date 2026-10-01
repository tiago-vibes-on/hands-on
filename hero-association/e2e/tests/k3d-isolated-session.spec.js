import { readFile, writeFile } from 'node:fs/promises'
import { expect, test } from '@playwright/test'

const appOrigin = 'https://app.e2e.heroassociation.test'
const statePath = '/session/bff-session.json'
const accountPath = '/session/account.json'

test('session state before BFF restart', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('user1@mail.com')
  await page.locator('#password').fill('user1')
  await page.locator('#kc-login').click()
  await expect(page).toHaveURL(`${appOrigin}/`)

  const response = await page.request.get('/api/v1/account', { maxRedirects: 0 })
  expect(response.status()).toBe(200)
  const account = await response.json()
  expect(account.keycloakSubject).toBeTruthy()
  await page.context().storageState({ path: statePath })
  await writeFile(accountPath, JSON.stringify({ id: account.id, subject: account.keycloakSubject }), {
    mode: 0o600,
  })
})

test('session state after BFF restart', async ({ browser }) => {
  const expected = JSON.parse(await readFile(accountPath, 'utf8'))
  const context = await browser.newContext({ storageState: statePath, ignoreHTTPSErrors: true })
  try {
    const page = await context.newPage()
    const response = await page.request.get(`${appOrigin}/api/v1/account`, { maxRedirects: 0 })
    expect(response.status()).toBe(200)
    expect(await response.json()).toMatchObject({
      id: expected.id,
      keycloakSubject: expected.subject,
    })
  } finally {
    await context.close()
  }
})
