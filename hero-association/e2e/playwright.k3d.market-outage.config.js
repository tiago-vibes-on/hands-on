import base from './playwright.k3d.config.js'

export default {
  ...base,
  testMatch: ['k3d-market-outage.spec.js'],
}
