import { expect, test } from '@playwright/test'

function positiveInteger(name, fallback, maximum) {
  const raw = process.env[name]
  const value = raw ? Number(raw) : fallback
  if (!Number.isSafeInteger(value) || value < 1 || value > maximum) {
    throw new Error(`${name} must be an integer between 1 and ${maximum}`)
  }
  return value
}

const seconds = positiveInteger('HERO_ASSOCIATION_K3D_LOAD_SECONDS', 60, 600)
const clients = positiveInteger('HERO_ASSOCIATION_K3D_LOAD_CLIENTS', 4, 32)

function percentile(sorted, percent) {
  return sorted[Math.ceil((percent / 100) * sorted.length) - 1]
}

test('measure sustained authenticated agency-state reads through k3d', async ({ page }) => {
  test.setTimeout(seconds * 1_000 + 90_000)

  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill('user1@mail.com')
  await page.locator('#password').fill('user1')
  await page.locator('#kc-login').click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()

  const accountResponse = await page.request.get('/api/v1/account')
  expect(accountResponse.ok()).toBeTruthy()
  const account = await accountResponse.json()
  const agencyId = account.agencyMemberships[0].agencyId
  await accountResponse.dispose()
  const api = page.context().request
  await page.close()

  const path = `/api/v1/agencies/${agencyId}/state`
  const latencies = []
  const statuses = new Map()
  let transportErrors = 0
  const started = performance.now()
  const deadline = started + seconds * 1_000

  async function readUntilDeadline() {
    while (performance.now() < deadline) {
      const requestStarted = performance.now()
      try {
        const response = await api.get(path, { timeout: 15_000 })
        const status = response.status()
        statuses.set(status, (statuses.get(status) || 0) + 1)
        latencies.push(performance.now() - requestStarted)
        await response.dispose()
      } catch (error) {
        transportErrors += 1
        console.error(`Agency-state request failed: ${error.message}`)
      }
      await new Promise((resolve) => setTimeout(resolve, 100))
    }
  }

  await Promise.all(Array.from({ length: clients }, readUntilDeadline))
  const elapsedSeconds = (performance.now() - started) / 1_000
  latencies.sort((left, right) => left - right)
  const failedResponses = [...statuses.entries()]
    .filter(([status]) => status !== 200)
    .reduce((total, [, count]) => total + count, 0)
  const result = {
    clients,
    elapsedSeconds: Number(elapsedSeconds.toFixed(1)),
    requests: latencies.length,
    requestsPerSecond: Number((latencies.length / elapsedSeconds).toFixed(1)),
    statuses: Object.fromEntries(statuses),
    transportErrors,
    latencyMs: latencies.length ? {
      p50: Math.round(percentile(latencies, 50)),
      p95: Math.round(percentile(latencies, 95)),
      p99: Math.round(percentile(latencies, 99)),
    } : null,
  }
  console.log(`K3D_LOAD_RESULT ${JSON.stringify(result)}`)

  expect(latencies.length).toBeGreaterThan(0)
  expect(failedResponses + transportErrors).toBe(0)
})
