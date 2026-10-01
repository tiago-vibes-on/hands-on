import { expect, test } from '@playwright/test'

const appOrigin = 'https://app.e2e.heroassociation.test'
const statePath = '/session/bff-session.json'

test('BFF Redis outage fails closed', async ({ browser }) => {
  const context = await browser.newContext({ storageState: statePath, ignoreHTTPSErrors: true })
  try {
    const page = await context.newPage()
    const response = await page.request.get(`${appOrigin}/api/v1/account`, { maxRedirects: 0 })
    expect([302, 401, 403, 500, 502, 503, 504]).toContain(response.status())
  } finally {
    await context.close()
  }
})

test('fresh login works after BFF Redis recovery', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('user1@mail.com')
  await page.locator('#password').fill('user1')
  await page.locator('#kc-login').click()
  await expect(page).toHaveURL(`${appOrigin}/`)
  const response = await page.request.get('/api/v1/account', { maxRedirects: 0 })
  expect(response.status()).toBe(200)
  expect(await response.json()).toMatchObject({ email: 'user1@mail.com' })
  await page.context().storageState({ path: statePath })
})

test('expired BFF token state fails closed', async ({ browser }) => {
  const context = await browser.newContext({ storageState: statePath, ignoreHTTPSErrors: true })
  try {
    const page = await context.newPage()
    const response = await page.request.get(`${appOrigin}/api/v1/account`, { maxRedirects: 0 })
    expect([302, 401, 403]).toContain(response.status())
  } finally {
    await context.close()
  }
})
