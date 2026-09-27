import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  testIgnore: /k3d-.*\.spec\.js$/,
  fullyParallel: false,
  workers: 1,
  ...(process.env.E2E_EXTERNAL_STACK === 'true' ? {} : { globalSetup: './global-setup.js' }),
  timeout: 60_000,
  expect: {
    timeout: 10_000,
  },
  reporter: process.env.CI === 'true' ? 'github' : 'list',
  use: {
    baseURL: 'https://heroassociation.test',
    ignoreHTTPSErrors: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
})
