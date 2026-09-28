import { expect, test } from '@playwright/test'

function positiveInteger(name, fallback, maximum) {
  const value = Number(process.env[name] || fallback)
  if (!Number.isSafeInteger(value) || value < 1 || value > maximum) {
    throw new Error(`${name} must be an integer between 1 and ${maximum}`)
  }
  return value
}

const seconds = positiveInteger('HERO_ASSOCIATION_K3D_MIXED_SECONDS', 60, 600)
const clients = positiveInteger('HERO_ASSOCIATION_K3D_MIXED_CLIENTS', 4, 128)
const capacityHeader = { 'X-Hero-Association-Capacity-Test': 'capacity' }

function percentile(values, rank) {
  return values[Math.ceil((rank / 100) * values.length) - 1]
}

function summary(statistics) {
  statistics.latencies.sort((left, right) => left - right)
  return {
    requests: statistics.latencies.length,
    statuses: Object.fromEntries(statistics.statuses),
    transportErrors: statistics.transportErrors,
    latencyMs: statistics.latencies.length ? {
      p50: Math.round(percentile(statistics.latencies, 50)),
      p95: Math.round(percentile(statistics.latencies, 95)),
      p99: Math.round(percentile(statistics.latencies, 99)),
    } : null,
  }
}

test('measure authenticated reads and activity writes against isolated k3d Core', async ({ page }) => {
  test.setTimeout(seconds * 1_000 + 120_000)

  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('user1@mail.com')
  await page.locator('#password').fill('user1')
  await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()

  const sessionResponse = await page.request.get('/api/v1/session')
  expect(sessionResponse.status()).toBe(200)
  const { csrfToken } = await sessionResponse.json()
  await sessionResponse.dispose()
  expect(csrfToken).toBeTruthy()

  const api = page.context().request
  const accountResponse = await api.get('/api/v1/account', {
    headers: capacityHeader,
    maxRedirects: 0,
  })
  expect(accountResponse.status()).toBe(200)
  const account = await accountResponse.json()
  await accountResponse.dispose()
  const agencyId = account.agencyMemberships[0].agencyId
  const path = `/api/v1/agencies/${agencyId}/state`

  const isolatedResponse = await api.get(path, {
    headers: capacityHeader,
    maxRedirects: 0,
  })
  expect(isolatedResponse.status()).toBe(200)
  const state = await isolatedResponse.json()
  await isolatedResponse.dispose()
  expect(state.agency.name).toBe('Capacity Lab Agency')

  const liveResponse = await api.get(path, { maxRedirects: 0 })
  expect(liveResponse.status()).toBe(200)
  const liveState = await liveResponse.json()
  await liveResponse.dispose()
  expect(liveState.agency.name).not.toBe('Capacity Lab Agency')
  const agencyHeroes = state.heroes.filter((hero) => hero.partyId === null && hero.activity !== 'ON_QUEST')
  expect(agencyHeroes.length).toBeGreaterThan(0)
  await page.close()

  const statistics = {
    read: { latencies: [], statuses: new Map(), transportErrors: 0 },
    write: { latencies: [], statuses: new Map(), transportErrors: 0 },
  }
  const failureSamples = []
  const started = performance.now()
  const deadline = started + seconds * 1_000

  async function work(clientIndex) {
    let requestIndex = 0
    while (performance.now() < deadline) {
      const isWrite = requestIndex % 10 === 9
      const operation = isWrite ? 'write' : 'read'
      const stats = statistics[operation]
      const requestStarted = performance.now()
      try {
        const response = isWrite
          ? await api.put(
            `/api/v1/agencies/${agencyId}/heroes/${agencyHeroes[clientIndex % agencyHeroes.length].id}/activity`,
            {
              data: { activity: Math.floor(requestIndex / 10) % 2 === 0 ? 'RESTING' : 'TRAINING' },
              headers: { ...capacityHeader, 'X-CSRF-TOKEN': csrfToken },
              maxRedirects: 0,
              timeout: 15_000,
            },
          )
          : await api.get(path, {
            headers: capacityHeader,
            maxRedirects: 0,
            timeout: 15_000,
          })
        const status = response.status()
        stats.statuses.set(status, (stats.statuses.get(status) || 0) + 1)
        stats.latencies.push(performance.now() - requestStarted)
        if (status !== 200 && failureSamples.length < 5) {
          failureSamples.push({
            operation,
            second: Math.round((requestStarted - started) / 1_000),
            status,
            body: (await response.text()).slice(0, 160),
          })
        }
        await response.dispose()
      } catch (error) {
        stats.transportErrors += 1
        if (failureSamples.length < 5) {
          failureSamples.push({
            operation,
            second: Math.round((requestStarted - started) / 1_000),
            error: error.message.slice(0, 160),
          })
        }
      }
      requestIndex += 1
      await new Promise((resolve) => setTimeout(resolve, 100))
    }
  }

  await Promise.all(Array.from({ length: clients }, (_, index) => work(index)))
  const elapsedSeconds = (performance.now() - started) / 1_000
  const read = summary(statistics.read)
  const write = summary(statistics.write)
  console.log(`K3D_MIXED_RESULT ${JSON.stringify({
    clients,
    elapsedSeconds: Number(elapsedSeconds.toFixed(1)),
    requestsPerSecond: Number(((read.requests + write.requests) / elapsedSeconds).toFixed(1)),
    read,
    write,
    failureSamples,
  })}`)

  expect(read.requests).toBeGreaterThan(0)
  expect(write.requests).toBeGreaterThan(0)
})
