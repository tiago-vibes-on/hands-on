import { expect, test } from '@playwright/test'
import { marketLimitBurst } from '../market-limit-burst.js'

const appUrl = 'https://heroassociation.test'
const dawnwatchAgencyId = '019c4c00-0001-7000-8000-000000000001'
const ironridgeAgencyId = '019c4c00-0001-7000-8000-000000000002'

async function signIn(page, email, password) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.locator('#kc-form-login')).toBeVisible()
  await page.locator('#username').fill(email)
  await page.locator('#password').fill(password)
  await page.locator('#kc-login').click()
  await expect(page).toHaveURL(`${appUrl}/`)
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
}

async function csrfToken(page) {
  const response = await page.request.get('/api/v1/session')
  expect(response.status()).toBe(200)
  return (await response.json()).csrfToken
}

function placeInvalidOrder(page, agencyId, token, extraHeaders = {}) {
  // Core rejects this missing order data; the rate test does not change game state.
  return page.request.post(`/api/v1/market/orders`, {
    data: { agencyId },
    headers: { 'X-CSRF-TOKEN': token, ...extraHeaders },
    maxRedirects: 0,
  })
}

test('k3d gateway enforces a shared market limit while BFF has two Pods', async ({ browser }) => {
  const firstContext = await browser.newContext({ ignoreHTTPSErrors: true })
  const secondContext = await browser.newContext({ ignoreHTTPSErrors: true })
  const otherUserContext = await browser.newContext({ ignoreHTTPSErrors: true })

  try {
    const firstPage = await firstContext.newPage()
    const secondPage = await secondContext.newPage()
    const otherUserPage = await otherUserContext.newPage()
    await signIn(firstPage, 'user1@mail.com', 'user1')
    await signIn(secondPage, 'user1@mail.com', 'user1')
    await signIn(otherUserPage, 'user2@mail.com', 'user2')

    const user1Subject = (await (await firstPage.request.get('/api/v1/session')).json()).identity.subject
    const firstToken = await csrfToken(firstPage)
    const secondToken = await csrfToken(secondPage)
    const otherUserToken = await csrfToken(otherUserPage)

    const responses = await marketLimitBurst(() => Promise.all([
      ...Array.from({ length: 3 }, () => placeInvalidOrder(firstPage, dawnwatchAgencyId, firstToken)),
      ...Array.from({ length: 3 }, () => placeInvalidOrder(secondPage, dawnwatchAgencyId, secondToken)),
    ]))
    expect(responses.map((response) => response.status()).sort()).toEqual([400, 400, 400, 400, 400, 429])
    const limitedResponse = responses.find((response) => response.status() === 429)
    expect(limitedResponse.headers()['retry-after']).toBe('1')
    expect(limitedResponse.headers()['x-hero-association-rate-limit-layer']).toBe('envoy')

    const otherUserResponse = await placeInvalidOrder(otherUserPage, ironridgeAgencyId, otherUserToken, {
      'X-Hero-Association-Subject': user1Subject,
    })
    expect(otherUserResponse.status()).toBe(400)
  } finally {
    await Promise.all([firstContext.close(), secondContext.close(), otherUserContext.close()])
  }
})

test('an anonymous market placement cannot forge a user identity', async ({ request }) => {
  const sessionResponse = await request.get('/api/v1/session')
  expect(sessionResponse.status()).toBe(200)
  const token = (await sessionResponse.json()).csrfToken
  const response = await request.post('/api/v1/market/orders', {
    data: { agencyId: dawnwatchAgencyId },
    headers: { 'X-CSRF-TOKEN': token, 'X-Hero-Association-Subject': 'forged-subject' },
    maxRedirects: 0,
  })
  expect(response.status()).toBe(401)
})
