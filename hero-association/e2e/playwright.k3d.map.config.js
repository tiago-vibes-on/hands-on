import { defineConfig } from '@playwright/test'
import expeditionConfig from './playwright.k3d.expedition.config.js'

export default defineConfig({
  ...expeditionConfig,
  testMatch: 'expedition.spec.js',
})
