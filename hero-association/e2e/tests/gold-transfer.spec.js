import { expect, test } from '@playwright/test'

const dawnwatchAgencyId = '019c4c00-0001-7000-8000-000000000001'

test('an agency leader moves gold to self and back without a fee', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('user1@mail.com')
  await page.locator('#password').fill('user1')
  await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()

  const initialAgency = await (await page.request.get(`/api/v1/agencies/${dawnwatchAgencyId}/state`)).json()
  const initialAccount = await (await page.request.get('/api/v1/account')).json()

  await page.getByRole('button', { name: 'Agency' }).click()
  const withdrawal = page.getByRole('form', { name: 'Send agency gold to a Manager' })
  await expect(withdrawal).toBeVisible()
  await withdrawal.getByRole('textbox', { name: 'Recipient Manager name' }).fill('User 1')
  await withdrawal.getByRole('spinbutton', { name: 'Gold amount' }).fill('1')
  await withdrawal.getByRole('button', { name: 'Send gold' }).click()
  await expect(page.getByRole('status')).toContainText('Moved 1 gold from Dawnwatch Agency to User 1.')

  const afterWithdrawal = await (await page.request.get('/api/v1/account')).json()
  expect(afterWithdrawal.manager.gold).toBe(initialAccount.manager.gold + 1)

  const deposit = page.getByRole('form', { name: 'Send personal gold to an agency' })
  await deposit.getByRole('spinbutton', { name: 'Gold amount' }).fill('1')
  await deposit.getByRole('button', { name: 'Send gold' }).click()
  await expect(page.getByRole('status')).toContainText('Moved 1 gold to Dawnwatch Agency.')

  const finalAgency = await (await page.request.get(`/api/v1/agencies/${dawnwatchAgencyId}/state`)).json()
  const finalAccount = await (await page.request.get('/api/v1/account')).json()
  expect(finalAccount.manager.gold).toBe(initialAccount.manager.gold)
  expect(finalAgency.agency.gold).toBe(initialAgency.agency.gold)
})
