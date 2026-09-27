import { expect, test } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import net from 'node:net'

const redisHost = 'host.docker.internal'
const redisPort = 16380
const primaryBffUrl = 'https://heroassociation.test'
const secondaryBffUrl = 'https://heroassociation.test/__e2e-secondary'
const dawnwatchAgencyId = '019c4c00-0001-7000-8000-000000000001'
const ironridgeAgencyId = '019c4c00-0001-7000-8000-000000000002'

function encodeRedisCommand(argumentsForCommand) {
  return Buffer.from(`*${argumentsForCommand.length}\r\n${argumentsForCommand
    .map((argument) => `$${Buffer.byteLength(argument)}\r\n${argument}\r\n`)
    .join('')}`)
}

function parseRedisResponse(buffer, offset = 0) {
  if (offset >= buffer.length) {
    return undefined
  }

  const lineEnd = buffer.indexOf('\r\n', offset)
  if (lineEnd === -1) {
    return undefined
  }

  const marker = String.fromCharCode(buffer[offset])
  const header = buffer.toString('utf8', offset + 1, lineEnd)
  const contentOffset = lineEnd + 2

  if (marker === '+' || marker === ':') {
    return { value: marker === ':' ? Number(header) : header, nextOffset: contentOffset }
  }

  if (marker === '-') {
    throw new Error(`Redis command failed: ${header}`)
  }

  if (marker === '$') {
    const size = Number(header)
    if (size === -1) {
      return { value: null, nextOffset: contentOffset }
    }

    const endOffset = contentOffset + size
    if (buffer.length < endOffset + 2) {
      return undefined
    }

    return {
      value: buffer.toString('utf8', contentOffset, endOffset),
      nextOffset: endOffset + 2,
    }
  }

  if (marker === '*') {
    const count = Number(header)
    if (count === -1) {
      return { value: null, nextOffset: contentOffset }
    }

    const values = []
    let nextOffset = contentOffset
    for (let index = 0; index < count; index += 1) {
      const item = parseRedisResponse(buffer, nextOffset)
      if (item === undefined) {
        return undefined
      }
      values.push(item.value)
      nextOffset = item.nextOffset
    }

    return { value: values, nextOffset }
  }

  throw new Error(`Unsupported Redis response marker: ${marker}`)
}

function redisCommand(...argumentsForCommand) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: redisHost, port: redisPort })
    let response = Buffer.alloc(0)
    let settled = false

    const finish = (callback, value) => {
      if (settled) {
        return
      }
      settled = true
      socket.destroy()
      callback(value)
    }

    socket.setTimeout(5_000)
    socket.once('connect', () => socket.write(encodeRedisCommand(argumentsForCommand)))
    socket.on('data', (chunk) => {
      response = Buffer.concat([response, chunk])
      try {
        const parsed = parseRedisResponse(response)
        if (parsed !== undefined) {
          finish(resolve, parsed.value)
        }
      } catch (error) {
        finish(reject, error)
      }
    })
    socket.once('timeout', () => finish(reject, new Error('Timed out waiting for Redis')))
    socket.once('error', (error) => finish(reject, error))
  })
}

async function redisTokenKeys() {
  return redisCommand('KEYS', 'oidc:token:*')
}

async function signIn(page, email = 'user1@mail.com', password = 'user1') {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/https:\/\/auth\.heroassociation\.test\/realms\/hero-association\//)
  await page.locator('#username').fill(email)
  await page.locator('#password').fill(password)
  await page.locator('#kc-login').click()
  await expect(page).toHaveURL('https://heroassociation.test/')
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
}


test.beforeEach(async () => {
  await redisCommand('FLUSHDB')
})
test('returns an already signed-out logout visit to the frontend', async ({ page }) => {
  await page.goto('/auth/logout')
  await expect(page).toHaveURL('https://heroassociation.test/')
  await expect(page.getByRole('heading', { name: 'Welcome to Hero Association' })).toBeVisible()
})

test('registers a new player, signs out, and signs back into the same account', async ({ page }) => {
  const email = `e2e-${randomUUID()}@example.test`
  const password = 'E2eAccount123!'

  await page.goto('/')

  await expect(page.getByRole('button', { name: 'Create account' })).toBeVisible()
  await page.getByRole('button', { name: 'Create account' }).click()

  await expect(page).toHaveURL(/https:\/\/auth\.heroassociation\.test\/realms\/hero-association\//)
  await expect(page.locator('#kc-register-form')).toBeVisible()
  await expect(page.locator('#firstName')).toHaveCount(0)
  await expect(page.locator('#lastName')).toHaveCount(0)

  await page.locator('#email').fill(email)
  await page.locator('#password').fill(password)
  await page.locator('#password-confirm').fill(password)
  await page.getByRole('button', { name: 'Register', exact: true }).click()

  await expect(page).toHaveURL('https://heroassociation.test/')
  await expect(page.getByRole('heading', { name: 'Choose your manager name' })).toBeVisible()

  const firstAccountResponse = await page.request.get(`${primaryBffUrl}/api/v1/account`, { maxRedirects: 0 })
  expect(firstAccountResponse.status()).toBe(200)
  const firstAccount = await firstAccountResponse.json()
  expect(firstAccount).toMatchObject({ email, manager: null, agencyMemberships: [] })
  expect(firstAccount.id).toBeTruthy()
  expect(firstAccount.keycloakSubject).toBeTruthy()

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Welcome to Hero Association' })).toBeVisible()

  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.locator('#kc-form-login')).toBeVisible()
  await page.locator('#username').fill(email)
  await page.locator('#password').fill(password)
  await page.locator('#kc-login').click()

  await expect(page).toHaveURL('https://heroassociation.test/')
  await expect(page.getByRole('heading', { name: 'Choose your manager name' })).toBeVisible()

  const secondAccountResponse = await page.request.get(`${primaryBffUrl}/api/v1/account`, { maxRedirects: 0 })
  expect(secondAccountResponse.status()).toBe(200)
  const secondAccount = await secondAccountResponse.json()
  expect(secondAccount).toMatchObject({
    id: firstAccount.id,
    keycloakSubject: firstAccount.keycloakSubject,
    email,
    manager: null,
    agencyMemberships: [],
  })
})

test('signs in and fully signs out of the Keycloak session', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Welcome to Hero Association' })).toBeVisible()
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page).toHaveURL(/https:\/\/auth\.heroassociation\.test\/realms\/hero-association\//)
  await page.locator('#username').fill('user1@mail.com')
  await page.locator('#password').fill('user1')
  await page.locator('#kc-login').click()

  await expect(page).toHaveURL('https://heroassociation.test/')
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  await expect(page.getByText('User 1', { exact: true })).toBeVisible()
  await expect(page.locator('.player-card__avatar')).toHaveText('U')
  await expect(page.getByText('Dawnwatch Agency', { exact: true }).first()).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).click()

  await expect(page).toHaveURL('https://heroassociation.test/')
  await expect(page.getByRole('heading', { name: 'Welcome to Hero Association' })).toBeVisible()

  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/https:\/\/auth\.heroassociation\.test\/realms\/hero-association\//)
  await expect(page.locator('#kc-form-login')).toBeVisible()
})

test('refreshes an expired token without leaving the BFF session', async ({ page }) => {
  await signIn(page)

  const firstSessionKeys = await redisTokenKeys()
  expect(firstSessionKeys).toHaveLength(1)

  await page.waitForTimeout(9_000)
  const response = await page.request.get(`${primaryBffUrl}/api/v1/account`, { maxRedirects: 0 })

  expect(response.status()).toBe(200)
  expect(await redisTokenKeys()).toHaveLength(1)
})

test('shares a Redis-backed session with a second BFF instance', async ({ page }) => {
  await signIn(page)

  const sessionKeys = await redisTokenKeys()
  expect(sessionKeys).toHaveLength(1)
  expect(await redisCommand('TTL', sessionKeys[0])).toBeGreaterThan(0)

  const response = await page.request.get(`${secondaryBffUrl}/api/v1/account`, { maxRedirects: 0 })
  expect(response.status()).toBe(200)
})

test('expires Redis token state and fails closed', async ({ page }) => {
  await signIn(page)

  const sessionKeys = await redisTokenKeys()
  expect(sessionKeys).toHaveLength(1)
  expect(await redisCommand('EXPIRE', sessionKeys[0], '1')).toBe(1)
  await page.waitForTimeout(1_100)

  expect(await redisTokenKeys()).toEqual([])
  const response = await page.request.get(`${primaryBffUrl}/api/v1/account`, { maxRedirects: 0 })
  expect(response.status()).not.toBe(200)
})

test('onboards a manager and creates their first agency', async ({ page }) => {
  await signIn(page, 'user3@mail.com', 'user3')

  await expect(page.getByRole('heading', { name: 'Choose your manager name' })).toBeVisible()
  await page.getByLabel('Manager name').fill('New Arrival')
  await page.getByRole('button', { name: 'Create manager' }).click()
  await expect(page.getByRole('heading', { name: 'Create your agency' })).toBeVisible()
  await expect(page.getByText('Manager New Arrival', { exact: true })).toBeVisible()

  const blockedAgency = await page.request.get(`${primaryBffUrl}/api/v1/agencies/${dawnwatchAgencyId}/state`, {
    maxRedirects: 0,
  })
  expect(blockedAgency.status()).toBe(403)

  await page.getByLabel('Agency name').fill('New Arrival Agency')
  await page.getByRole('button', { name: 'Create agency' }).click()
  await expect(page.getByText('New Arrival Agency', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('No heroes yet', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Heroes', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Heroes', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Recruit Steelward' })).toBeVisible()
  await page.getByRole('button', { name: 'Recruit Steelward' }).click()
  await expect(page.getByRole('heading', { name: 'Steelward', exact: true })).toBeVisible()

  const account = await (await page.request.get(`${primaryBffUrl}/api/v1/account`, { maxRedirects: 0 })).json()
  const agencyId = account.agencyMemberships[0].agencyId
  let createdState
  await expect.poll(async () => {
    const createdAgency = await page.request.get(`${primaryBffUrl}/api/v1/agencies/${agencyId}/state`, {
      maxRedirects: 0,
    })
    expect(createdAgency.status()).toBe(200)
    createdState = await createdAgency.json()
    return createdState.heroes.length
  }).toBe(1)
  expect(createdState.agency).toMatchObject({
    id: agencyId,
    name: 'New Arrival Agency',
    gold: 0,
    reputation: 0,
  })
  expect(createdState.heroes[0]).toMatchObject({
    alias: 'Steelward',
    level: 1,
    activity: 'TRAINING',
    stamina: 100,
  })
})

test('prevents a manager from reading another agency', async ({ page }) => {
  await signIn(page, 'user2@mail.com', 'user2')

  await expect(page.getByText('Ironridge Exchange', { exact: true }).first()).toBeVisible()
  const ownAgency = await page.request.get(`${primaryBffUrl}/api/v1/agencies/${ironridgeAgencyId}/state`, {
    maxRedirects: 0,
  })
  expect(ownAgency.status()).toBe(200)

  const otherAgency = await page.request.get(`${primaryBffUrl}/api/v1/agencies/${dawnwatchAgencyId}/state`, {
    maxRedirects: 0,
  })
  expect(otherAgency.status()).toBe(403)
})
