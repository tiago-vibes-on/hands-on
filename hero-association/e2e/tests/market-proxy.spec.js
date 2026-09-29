import { expect, test } from '@playwright/test'

const primaryBffUrl = 'https://heroassociation.test'
const secondaryBffUrl = 'https://heroassociation.test/__e2e-secondary'
const dawnwatchAgencyId = '019c4c00-0001-7000-8000-000000000001'
const ironridgeAgencyId = '019c4c00-0001-7000-8000-000000000002'

async function signIn(page, email, password) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.locator('#kc-form-login')).toBeVisible()
  await page.locator('#username').fill(email)
  await page.locator('#password').fill(password)
  await page.locator('#kc-login').click()
  await expect(page).toHaveURL('https://heroassociation.test/')
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
}

async function csrfToken(page) {
  const response = await page.request.get(`${primaryBffUrl}/api/v1/session`)
  expect(response.status()).toBe(200)
  return (await response.json()).csrfToken
}

function placeInvalidOrder(page, baseUrl, agencyId, token) {
  // Validation rejects this request in Core, so the test never changes market data.
  return page.request.post(`${baseUrl}/api/v1/market/orders`, {
    data: { agencyId },
    headers: { 'X-CSRF-TOKEN': token },
    maxRedirects: 0,
  })
}

test('forwards market placements through both local BFFs without rate limiting', async ({ browser }) => {
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

    const firstToken = await csrfToken(firstPage)
    const secondToken = await csrfToken(secondPage)
    const otherUserToken = await csrfToken(otherUserPage)

    const responses = await Promise.all([
      ...Array.from({ length: 3 }, () =>
        placeInvalidOrder(firstPage, primaryBffUrl, dawnwatchAgencyId, firstToken)),
      ...Array.from({ length: 3 }, () =>
        placeInvalidOrder(secondPage, secondaryBffUrl, dawnwatchAgencyId, secondToken)),
    ])
    expect(responses.map((response) => response.status()).sort()).toEqual([400, 400, 400, 400, 400, 400])

    const otherUserResponse = await placeInvalidOrder(
      otherUserPage, primaryBffUrl, ironridgeAgencyId, otherUserToken,
    )
    expect(otherUserResponse.status()).toBe(400)
  } finally {
    await Promise.all([firstContext.close(), secondContext.close(), otherUserContext.close()])
  }
})
