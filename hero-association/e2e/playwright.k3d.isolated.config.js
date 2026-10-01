import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  testMatch: [
    'k3d-isolated-authentication.spec.js',
    'k3d-isolated-agency.spec.js',
    'gold-transfer.spec.js',
    'market-ownership.spec.js',
    'k3d-isolated-market-rate-limit.spec.js',
    'expedition.spec.js',
    'k3d-expedition-api.spec.js',
  ],
  fullyParallel: false,
  workers: 1,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: 'list',
  use: {
    baseURL: 'https://app.e2e.heroassociation.test',
    ignoreHTTPSErrors: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
