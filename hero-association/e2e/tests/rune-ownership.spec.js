import { expect, test } from '@playwright/test'

const agencyId = '019c4c00-0001-7000-8000-000000000001'
const user1WarriorId = '019c4c00-0030-7001-8000-000000000001'

async function signIn(page, email, password) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill(email)
  await page.locator('#password').fill(password)
  await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
}

async function agencyState(page) {
  const response = await page.request.get(`/api/v1/agencies/${agencyId}/state`)
  expect(response.status()).toBe(200)
  return response.json()
}

function quantity(inventory, code) {
  return inventory.find((entry) => entry.rune.code === code)?.quantity ?? 0
}

test('moves an agency rune to a Hero, then to the Manager and a personal Hero', async ({ page }) => {
  await signIn(page, 'user1@mail.com', 'user1')
  const initial = await agencyState(page)
  const personalWarrior = initial.personalHeroes.find((hero) => hero.id === user1WarriorId)
  expect(personalWarrior?.runeSlots[0].rune).toBeNull()
  expect(quantity(initial.runeInventory, 'attack-rune')).toBe(1)

  await page.getByRole('button', { name: 'Heroes', exact: true }).click()
  const agencySlot = page.getByRole('group', { name: 'Emberveil rune slots' })
    .getByRole('button', { name: /Emberveil rune slot 5/ })
  await agencySlot.click()
  await page.getByRole('dialog').getByRole('region', { name: 'Agency inventory' })
    .getByRole('button', { name: /Attack Rune/ }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(agencySlot).toHaveAttribute('aria-label', /Attack Rune/)
  const equipped = await agencyState(page)
  expect(equipped.heroes.find((hero) => hero.alias === 'Emberveil').runeSlots[4].rune.code).toBe('attack-rune')
  expect(quantity(equipped.runeInventory, 'attack-rune')).toBe(0)

  await agencySlot.click()
  await page.getByRole('dialog').getByRole('button', { name: 'Unequip' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(agencySlot).toHaveAttribute('aria-label', /empty/)
  const unequipped = await agencyState(page)
  expect(quantity(unequipped.personalRuneInventory, 'attack-rune')).toBe(1)
  expect(quantity(unequipped.runeInventory, 'attack-rune')).toBe(0)

  const personalSlot = page.getByRole('group', { name: `${personalWarrior.alias} rune slots` })
    .getByRole('button', { name: new RegExp(`${personalWarrior.alias} rune slot 1`) })
  await personalSlot.click()
  await page.getByRole('dialog').getByRole('region', { name: 'Personal inventory' })
    .getByRole('button', { name: /Attack Rune/ }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(personalSlot).toHaveAttribute('aria-label', /Attack Rune/)
  await page.reload()
  await page.getByRole('button', { name: 'Heroes', exact: true }).click()
  await expect(page.getByRole('group', { name: `${personalWarrior.alias} rune slots` })
    .getByRole('button', { name: /Attack Rune/ })).toBeVisible()
  const finalState = await agencyState(page)
  expect(finalState.personalHeroes.find((hero) => hero.id === user1WarriorId).runeSlots[0].rune.code)
    .toBe('attack-rune')
  expect(quantity(finalState.personalRuneInventory, 'attack-rune')).toBe(0)
})

test('allows a member to use agency runes but blocks another Manager’s Hero and away Heroes', async ({ page }) => {
  await signIn(page, 'manager1@mail.com', 'manager1')
  const initial = await agencyState(page)
  const account = await (await page.request.get('/api/v1/account')).json()
  const vitalityRune = initial.runeInventory.find((entry) => entry.rune.code === 'vitality-rune')?.rune
  const oakshield = initial.heroes.find((hero) => hero.alias === 'Oakshield')
  const memberWarrior = initial.personalHeroes.find((hero) => hero.ownerManagerId === account.manager.id
    && hero.heroClass === 'WARRIOR')
  expect(vitalityRune).toBeTruthy()
  expect(quantity(initial.runeInventory, 'vitality-rune')).toBe(1)
  expect(oakshield?.runeSlots[4].rune).toBeNull()
  expect(memberWarrior?.runeSlots[4].rune).toBeNull()

  await page.getByRole('button', { name: 'Heroes', exact: true }).click()
  const personalSlot = page.getByRole('group', { name: `${memberWarrior.alias} rune slots` })
    .getByRole('button', { name: new RegExp(`${memberWarrior.alias} rune slot 5`) })
  await personalSlot.click()
  await page.getByRole('dialog').getByRole('region', { name: 'Agency inventory' })
    .getByRole('button', { name: /Vitality Rune/ }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(personalSlot).toHaveAttribute('aria-label', /Vitality Rune/)
  await personalSlot.click()
  await page.getByRole('dialog').getByRole('button', { name: 'Unequip' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(personalSlot).toHaveAttribute('aria-label', /empty/)
  const agencySlot = page.getByRole('group', { name: 'Oakshield rune slots' })
    .getByRole('button', { name: /Oakshield rune slot 5/ })
  await agencySlot.click()
  await page.getByRole('dialog').getByRole('region', { name: 'Personal inventory' })
    .getByRole('button', { name: /Vitality Rune/ }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(agencySlot).toHaveAttribute('aria-label', /Vitality Rune/)
  await agencySlot.click()
  await page.getByRole('dialog').getByRole('button', { name: 'Unequip' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(agencySlot).toHaveAttribute('aria-label', /empty/)
  const transferred = await agencyState(page)
  expect(quantity(transferred.personalRuneInventory, 'vitality-rune')).toBe(1)
  expect(quantity(transferred.runeInventory, 'vitality-rune')).toBe(0)

  const session = await (await page.request.get('/api/v1/session')).json()
  let headers = { 'X-CSRF-TOKEN': session.csrfToken }
  const party = transferred.parties.find((candidate) => candidate.ownerManagerId === account.manager.id
    && candidate.name === 'Main Party')
  expect(party?.heroIds).toHaveLength(3)
  for (const heroId of party.heroIds.filter((id) => id !== memberWarrior.id)) {
    const removed = await page.request.delete(`/api/v1/agencies/${agencyId}/parties/${party.id}/heroes/${heroId}`, {
      headers,
    })
    expect(removed.status()).toBe(200)
  }
  const quest = transferred.quests.find((candidate) => candidate.title === 'Lost Courier')
  expect(quest?.status).toBe('AVAILABLE')
  const started = await page.request.put(`/api/v1/agencies/${agencyId}/quests/${quest.id}/start`, {
    headers,
    data: { partyId: party.id, expectedBorrowingFeeGold: 0 },
  })
  expect(started.status()).toBe(200)
  expect((await started.json()).personalHeroes.find((hero) => hero.id === memberWarrior.id).activity)
    .toBe('ON_QUEST')
  await page.reload()
  await page.getByRole('button', { name: 'Heroes', exact: true }).click()
  const refreshedSession = await (await page.request.get('/api/v1/session')).json()
  headers = { 'X-CSRF-TOKEN': refreshedSession.csrfToken }
  const awaySlot = page.getByRole('group', { name: `${memberWarrior.alias} rune slots` })
    .getByRole('button', { name: new RegExp(`${memberWarrior.alias} rune slot 5`) })
  await expect(awaySlot).toBeDisabled()
  const equip = (heroId) => page.request.put(`/api/v1/agencies/${agencyId}/heroes/${heroId}/rune-slots/4`, {
    headers,
    data: { runeId: vitalityRune.id, sourceOwnerType: 'MANAGER' },
    maxRedirects: 0,
  })
  expect((await equip(user1WarriorId)).status()).toBe(404)
  expect((await equip(memberWarrior.id)).status()).toBe(409)
  expect(quantity((await agencyState(page)).personalRuneInventory, 'vitality-rune')).toBe(1)
})
