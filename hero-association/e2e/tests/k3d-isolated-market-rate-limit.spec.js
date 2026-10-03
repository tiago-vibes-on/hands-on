import { expect, test } from '@playwright/test'
import { marketLimitBurst } from '../market-limit-burst.js'

const appOrigin = 'https://app.e2e.heroassociation.test'
const agencyId = '019c4c00-0001-7000-8000-000000000001'

async function signIn(page, email, password) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill(email)
  await page.locator('#password').fill(password)
  await page.locator('#kc-login').click()
  await expect(page).toHaveURL(`${appOrigin}/`)
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  const response = await page.request.get('/api/v1/session')
  expect(response.status()).toBe(200)
  return await response.json()
}

function invalidOrder(page, csrfToken, headers = {}) {
  // Core rejects the incomplete order; this test changes no market state.
  return page.request.post('/api/v1/market/orders', {
    data: { agencyId },
    headers: { 'X-CSRF-TOKEN': csrfToken, ...headers },
    maxRedirects: 0,
  })
}

test('isolated Envoy limits market placement per authenticated user across sessions', async ({ browser }) => {
  const contexts = await Promise.all(Array.from({ length: 3 }, () => browser.newContext({ ignoreHTTPSErrors: true })))
  try {
    const [first, second, other] = await Promise.all(contexts.map((context) => context.newPage()))
    const firstSession = await signIn(first, 'user1@mail.com', 'user1')
    const secondSession = await signIn(second, 'user1@mail.com', 'user1')
    const otherSession = await signIn(other, 'user2@mail.com', 'user2')

    expect(firstSession.identity.subject).toBe(secondSession.identity.subject)
    expect(otherSession.identity.subject).not.toBe(firstSession.identity.subject)

    const responses = await marketLimitBurst(() => Promise.all([
      ...Array.from({ length: 3 }, () => invalidOrder(first, firstSession.csrfToken)),
      ...Array.from({ length: 3 }, () => invalidOrder(second, secondSession.csrfToken)),
    ]))
    expect(responses.map((response) => response.status()).sort()).toEqual([400, 400, 400, 400, 400, 429])
    const limited = responses.find((response) => response.status() === 429)
    expect(limited.headers()['x-hero-association-rate-limit-layer']).toBe('envoy')
    expect(limited.headers()['retry-after']).toBe('1')

    const otherResponse = await invalidOrder(other, otherSession.csrfToken, {
      'X-Hero-Association-Subject': firstSession.identity.subject,
    })
    expect(otherResponse.status()).toBe(400)
  } finally {
    await Promise.all(contexts.map((context) => context.close()))
  }
})
