import { expect, test } from '@playwright/test'

const crystalId = '019c4c00-0070-7000-8000-000000000001'
function uuidV7() {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  for (let index = 0; index < 6; index++) bytes[index] = Math.floor(Date.now() / 2 ** (8 * (5 - index))) & 0xff
  bytes[6] = (bytes[6] & 0x0f) | 0x70
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
async function signIn(page, number) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill(`manager${number}@mail.com`)
  await page.locator('#password').fill(`manager${number}`)
  await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  return (await (await page.request.get('/api/v1/session')).json()).csrfToken
}
const manager = async (page) => (await (await page.request.get('/api/v1/account')).json()).manager
const itemQuantity = (owner) => owner.items.find((item) => item.id === crystalId)?.quantity ?? 0

test('real Assets settlement transfers items, applies the fee and refunds the buyer price improvement once', async ({ browser }) => {
  const sellerContext = await browser.newContext({ ignoreHTTPSErrors: true })
  const buyerContext = await browser.newContext({ ignoreHTTPSErrors: true })
  try {
    const seller = await sellerContext.newPage()
    const buyer = await buyerContext.newPage()
    const sellerCsrf = await signIn(seller, 3)
    const buyerCsrf = await signIn(buyer, 4)
    const sellerBefore = await manager(seller)
    const buyerBefore = await manager(buyer)
    const selling = await seller.request.post('/api/v1/market/orders', {
      headers: { 'X-CSRF-TOKEN': sellerCsrf },
      data: { placementId: uuidV7(), ownerType: 'MANAGER', side: 'SELL', itemId: crystalId, quantity: 1, priceGoldPerItem: 110 },
    })
    expect(selling.status(), await selling.text()).toBe(201)
    const payload = { placementId: uuidV7(), ownerType: 'MANAGER', side: 'BUY', itemId: crystalId, quantity: 1, priceGoldPerItem: 120 }
    const buying = await buyer.request.post('/api/v1/market/orders', { headers: { 'X-CSRF-TOKEN': buyerCsrf }, data: payload })
    expect(buying.status(), await buying.text()).toBe(201)
    const order = await buying.json()
    await expect.poll(async () => (await (await buyer.request.get(`/api/v1/market/orders/${order.id}`)).json()).status).toBe('FILLED')
    const buyerAfter = await manager(buyer)
    const sellerAfter = await manager(seller)
    expect(buyerAfter.gold).toBe(buyerBefore.gold - 110)
    expect(sellerAfter.gold).toBe(sellerBefore.gold + 99)
    expect(itemQuantity(buyerAfter)).toBe(itemQuantity(buyerBefore) + 1)
    expect(itemQuantity(sellerAfter)).toBe(itemQuantity(sellerBefore) - 1)
    const repeated = await buyer.request.post('/api/v1/market/orders', { headers: { 'X-CSRF-TOKEN': buyerCsrf }, data: payload })
    expect(repeated.status()).toBe(201)
    expect((await repeated.json()).id).toBe(order.id)
    expect((await manager(buyer)).gold).toBe(buyerAfter.gold)
    expect((await manager(seller)).gold).toBe(sellerAfter.gold)
  } finally {
    await buyerContext.close()
    await sellerContext.close()
  }
})
