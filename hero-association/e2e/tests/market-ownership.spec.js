import { expect, test } from '@playwright/test'

const ironIngotId = '019c4c00-0070-7000-8000-000000000002'

test('a Manager trades from a personal wallet without agency trading permission', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('manager4@mail.com')
  await page.locator('#password').fill('manager4')
  await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()

  await page.getByRole('button', { name: 'Market' }).click()
  await expect(page.getByRole('heading', { name: 'Market' })).toBeVisible()
  await expect(page.getByRole('combobox', { name: 'Trading as' })).toHaveValue('MANAGER')
  await expect(page.getByRole('combobox', { name: 'Trading as' }).locator('option')).toHaveCount(1)

  await page.getByRole('combobox', { name: 'Item' }).selectOption(ironIngotId)
  await page.getByRole('spinbutton', { name: 'Gold per item' }).fill('1')
  await page.getByRole('button', { name: 'Place order' }).click()

  const personalOffer = page.locator('.offer-row').filter({ hasText: 'Manager 4 · Manager' })
  await expect(personalOffer).toBeVisible()
  expect((await (await page.request.get('/api/v1/account')).json()).manager.gold).toBe(199)

  await personalOffer.getByRole('button', { name: 'Cancel' }).click()
  await expect(personalOffer).toHaveCount(0)
  expect((await (await page.request.get('/api/v1/account')).json()).manager.gold).toBe(200)
})
