import { expect, test } from '@playwright/test'

const appUrl = 'https://k3d.heroassociation.test'

test('gateway rejects market placement when rate-limit Redis is unavailable', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('user1@mail.com')
  await page.locator('#password').fill('user1')
  await page.locator('#kc-login').click()
  await expect(page).toHaveURL(`${appUrl}/`)

  const sessionResponse = await page.request.get('/api/v1/session')
  expect(sessionResponse.status()).toBe(200)
  const csrfToken = (await sessionResponse.json()).csrfToken
  const response = await page.request.post('/api/v1/market/orders', {
    data: { agencyId: '019c4c00-0001-7000-8000-000000000001' },
    headers: { 'X-CSRF-TOKEN': csrfToken },
    maxRedirects: 0,
  })
  expect(response.status()).toBe(500)
})
