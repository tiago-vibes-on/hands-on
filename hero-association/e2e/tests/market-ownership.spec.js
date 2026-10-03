import { expect, test } from '@playwright/test'

const ironIngotId = '019c4c00-0070-7000-8000-000000000002'

test('a Manager trades from a personal wallet without agency trading permission', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('manager4@mail.com')
  await page.locator('#password').fill('manager4')
  await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  const before = (await (await page.request.get('/api/v1/account')).json()).manager.gold

  await page.getByRole('button', { name: 'Market' }).click()
  await expect(page.getByRole('heading', { name: 'Market' })).toBeVisible()
  await expect(page.getByRole('combobox', { name: 'Trading as' })).toHaveValue('MANAGER')
  await expect(page.getByRole('combobox', { name: 'Trading as' }).locator('option')).toHaveCount(1)

  await page.getByRole('combobox', { name: 'Item' }).selectOption(ironIngotId)
  await page.getByRole('spinbutton', { name: 'Gold per item' }).fill('1')
  await page.getByRole('button', { name: 'Place order' }).click()

  const personalOffer = page.locator('.offer-row').filter({ hasText: 'Manager 4 · Manager' })
  await expect(personalOffer).toBeVisible()
  expect((await (await page.request.get('/api/v1/account')).json()).manager.gold).toBe(before - 1)

  await personalOffer.getByRole('button', { name: 'Cancel' }).click()
  await expect(personalOffer).toHaveCount(0)
  expect((await (await page.request.get('/api/v1/account')).json()).manager.gold).toBe(before)
})

async function personalMarket(page) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('manager4@mail.com')
  await page.locator('#password').fill('manager4')
  await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  await page.getByRole('button', { name: 'Market' }).click()
  await page.getByRole('combobox', { name: 'Item' }).selectOption(ironIngotId)
  await page.getByRole('spinbutton', { name: 'Gold per item' }).fill('1')
}

test('an interrupted placement keeps its ID across reload and an exact retry debits once', async ({ page }) => {
  await personalMarket(page)
  const before = (await (await page.request.get('/api/v1/account')).json()).manager.gold
  let payload
  await page.route('**/api/v1/market/orders', async (route) => {
    if (route.request().method() !== 'POST') return route.continue()
    payload = route.request().postDataJSON()
    const committed = await route.fetch()
    expect(committed.status()).toBe(201)
    await route.abort('failed')
  })
  await page.getByRole('button', { name: 'Place order' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Order placement pending' })).toBeVisible()
  expect(payload.placementId).toMatch(/^[0-9a-f-]{14}7[0-9a-f-]{21}$/)
  await expect.poll(async () => (await (await page.request.get('/api/v1/account')).json()).manager.gold).toBe(before - 1)
  const session = await (await page.request.get('/api/v1/session')).json()
  const repeat = await page.request.post('/api/v1/market/orders', {
    data: payload, headers: { 'X-CSRF-TOKEN': session.csrfToken },
  })
  expect(repeat.status()).toBe(201)
  const order = await repeat.json()
  expect((await (await page.request.get('/api/v1/account')).json()).manager.gold).toBe(before - 1)
  await page.unroute('**/api/v1/market/orders')
  await page.reload()
  await page.getByRole('button', { name: 'Market' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Order placement pending' })).toHaveCount(0)
  const placement = await (await page.request.get(`/api/v1/market/placements/${payload.placementId}`)).json()
  expect(placement).toMatchObject({ status: 'OPEN', orderId: order.id })
  const currentSession = await (await page.request.get('/api/v1/session')).json()
  expect((await page.request.delete(`/api/v1/market/orders/${order.id}`, {
    headers: { 'X-CSRF-TOKEN': currentSession.csrfToken },
  })).status()).toBe(200)
  expect((await (await page.request.get('/api/v1/account')).json()).manager.gold).toBe(before)
})

test('a pending placement response remains visible until its status confirms the order', async ({ page }) => {
  await personalMarket(page)
  let order
  await page.route('**/api/v1/market/orders', async (route) => {
    if (route.request().method() !== 'POST') return route.continue()
    const committed = await route.fetch()
    expect(committed.status()).toBe(201)
    order = await committed.json()
    await route.fulfill({ status: 202, contentType: 'application/json', body: JSON.stringify({
      id: route.request().postDataJSON().placementId, status: 'PENDING_RESERVATION', orderId: null,
    }) })
  })
  await page.getByRole('button', { name: 'Place order' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Order placement pending' })).toBeVisible()
  await expect(page.getByRole('status').filter({ hasText: 'Order placement pending' })).toHaveCount(0)
  const session = await (await page.request.get('/api/v1/session')).json()
  expect((await page.request.delete(`/api/v1/market/orders/${order.id}`, {
    headers: { 'X-CSRF-TOKEN': session.csrfToken },
  })).status()).toBe(200)
})
