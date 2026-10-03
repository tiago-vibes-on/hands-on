import { expect, test } from '@playwright/test'

const appOrigin = 'https://app.e2e.heroassociation.test'
const dawnwatchAgencyId = '019c4c00-0001-7000-8000-000000000001'
const ironridgeAgencyId = '019c4c00-0001-7000-8000-000000000002'

async function signIn(page, email, password) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill(email)
  await page.locator('#password').fill(password)
  await page.locator('#kc-login').click()
  await expect(page).toHaveURL(`${appOrigin}/`)
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
}

test('onboards a Manager and recruits for personal and agency rosters', async ({ page }) => {
  await signIn(page, 'user3@mail.com', 'user3')
  await expect(page.getByRole('heading', { name: 'Choose your manager name' })).toBeVisible()
  await page.getByLabel('Manager name').fill('New Arrival')
  await page.getByRole('button', { name: 'Create manager' }).click()
  await expect(page.getByRole('heading', { name: 'Create your agency' })).toBeVisible()

  const otherAgency = await page.request.get(`/api/v1/agencies/${dawnwatchAgencyId}/state`, {
    maxRedirects: 0,
  })
  expect(otherAgency.status()).toBe(403)

  await page.getByLabel('Agency name').fill('New Arrival Agency')
  await page.getByRole('button', { name: 'Create agency' }).click()
  await expect(page.getByText('New Arrival Agency', { exact: true }).first()).toBeVisible()
  const account = await (await page.request.get('/api/v1/account')).json()
  const agencyId = account.agencyMemberships[0].agencyId

  await page.getByRole('button', { name: 'Heroes', exact: true }).click()
  const recruitsResponse = await page.request.get('/api/v1/recruits')
  expect(recruitsResponse.status()).toBe(200)
  const recruits = await recruitsResponse.json()
  const steelwardId = recruits.find((recruit) => recruit.alias === 'Steelward')?.id
  const dawnflameId = recruits.find((recruit) => recruit.alias === 'Dawnflame')?.id
  expect(steelwardId).toBeTruthy()
  expect(dawnflameId).toBeTruthy()

  await page.getByRole('button', { name: 'Recruit Steelward for me' }).click()
  await expect(page.getByRole('heading', { name: 'Alden Steelward' })).toBeVisible()
  await page.getByRole('button', { name: 'Recruit Dawnflame for agency' }).click()
  await expect(page.getByRole('button', { name: 'Recruit Dawnflame for agency' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Dawnflame', exact: true })).toBeVisible()

  const stateResponse = await page.request.get(`/api/v1/agencies/${agencyId}/state`)
  expect(stateResponse.status()).toBe(200)
  const state = await stateResponse.json()
  expect(state.agency).toMatchObject({ id: agencyId, name: 'New Arrival Agency', gold: 0 })
  expect(state.personalHeroes).toHaveLength(4)
  expect(state.personalHeroes.map((hero) => hero.id)).toContain(steelwardId)
  expect(state.heroes).toHaveLength(1)
  expect(state.heroes[0]).toMatchObject({ id: dawnflameId, level: 1 })

  const session = await (await page.request.get('/api/v1/session')).json()
  const claim = (claimPath) => page.request.post(claimPath, {
    headers: { 'X-CSRF-TOKEN': session.csrfToken },
    maxRedirects: 0,
  })
  expect((await claim(`/api/v1/agencies/${agencyId}/recruits/${steelwardId}/claim`)).status()).toBe(409)
  expect((await claim(`/api/v1/recruits/${dawnflameId}/claim`)).status()).toBe(409)
  expect((await claim(`/api/v1/agencies/${agencyId}/recruits/${dawnflameId}/claim`)).status()).toBe(409)
})

test('lets the agency leader edit a hero borrowing fee', async ({ page }) => {
  await signIn(page, 'user1@mail.com', 'user1')
  await page.getByRole('button', { name: 'Heroes', exact: true }).click()

  const oakshieldCard = page.locator('.hero-card').filter({
    has: page.getByRole('heading', { name: 'Oakshield', exact: true }),
  })
  await oakshieldCard.locator('.hero-card__fee-editor input').fill('7')
  await oakshieldCard.getByRole('button', { name: 'Save fee' }).click()
  await expect(oakshieldCard).toContainText('7 gold future borrowing fee')

  const stateResponse = await page.request.get(`/api/v1/agencies/${dawnwatchAgencyId}/state`)
  expect(stateResponse.status()).toBe(200)
  const state = await stateResponse.json()
  expect(state.heroes.find((hero) => hero.alias === 'Oakshield')?.borrowingFeeGold).toBe(7)
})

test('blocks agency recruitment by a non-leader', async ({ page }) => {
  await signIn(page, 'manager9@mail.com', 'manager9')
  await page.getByRole('button', { name: 'Heroes', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Recruit Windmark for me' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Recruit Windmark for agency' })).toHaveCount(0)

  const account = await (await page.request.get('/api/v1/account')).json()
  const agencyId = account.agencyMemberships[0].agencyId
  const recruits = await (await page.request.get('/api/v1/recruits')).json()
  const windmarkId = recruits.find((recruit) => recruit.alias === 'Windmark')?.id
  expect(windmarkId).toBeTruthy()
  const session = await (await page.request.get('/api/v1/session')).json()
  const blocked = await page.request.post(
    `/api/v1/agencies/${agencyId}/recruits/${windmarkId}/claim`,
    { headers: { 'X-CSRF-TOKEN': session.csrfToken }, maxRedirects: 0 },
  )
  expect(blocked.status()).toBe(403)
})

test('prevents a Manager from reading another agency', async ({ page }) => {
  await signIn(page, 'user2@mail.com', 'user2')
  expect((await page.request.get(`/api/v1/agencies/${ironridgeAgencyId}/state`)).status()).toBe(200)
  expect((await page.request.get(`/api/v1/agencies/${dawnwatchAgencyId}/state`)).status()).toBe(403)
})
