import { chromium } from '@playwright/test'
import { writeFile } from 'node:fs/promises'

const appUrl = process.env.HERO_ASSOCIATION_K6_APP_URL || 'https://heroassociation.test:8443'
const outputPath = '/session/session.json'
const users = [
  ['user1@mail.com', 'user1'],
  ['user1@mail.com', 'user1'],
  ['user2@mail.com', 'user2'],
]

async function signIn(browser, email, password) {
  const context = await browser.newContext({ ignoreHTTPSErrors: true })
  try {
    const page = await context.newPage()
    await page.goto(appUrl)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await page.locator('#username').fill(email)
    await page.locator('#password').fill(password)
    await page.locator('#kc-login').click()
    await page.waitForURL(`${appUrl}/`)
    await page.getByRole('button', { name: 'Sign out' }).waitFor()

    const sessionResponse = await page.request.get(`${appUrl}/api/v1/session`)
    const accountResponse = await page.request.get(`${appUrl}/api/v1/account`)
    if (!sessionResponse.ok() || !accountResponse.ok()) {
      throw new Error('The authenticated session or account endpoint failed')
    }
    const session = await sessionResponse.json()
    const account = await accountResponse.json()
    const agencyId = account.agencyMemberships?.[0]?.agencyId
    const cookies = await context.cookies(appUrl)
    if (!session.authenticated || !session.csrfToken || !agencyId || cookies.length === 0) {
      throw new Error('The authenticated session is missing CSRF, agency, or cookie data')
    }
    return {
      agencyId,
      csrfToken: session.csrfToken,
      cookieHeader: cookies.map(({ name, value }) => `${name}=${value}`).join('; '),
    }
  } finally {
    await context.close()
  }
}

const browser = await chromium.launch()
try {
  const sessions = []
  for (const [email, password] of users) {
    sessions.push(await signIn(browser, email, password))
  }
  if (sessions[0].agencyId !== sessions[1].agencyId || sessions[0].agencyId === sessions[2].agencyId) {
    throw new Error('The seeded users do not have the expected separate agency memberships')
  }
  await writeFile(outputPath, JSON.stringify({ appUrl, sessions }), { mode: 0o600 })
  console.log('Prepared three authenticated k3d sessions for k6 (two for user1, one for user2).')
} finally {
  await browser.close()
}
