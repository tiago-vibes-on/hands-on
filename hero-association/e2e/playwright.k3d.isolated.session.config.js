import base from './playwright.k3d.isolated.config.js'

export default {
  ...base,
  testMatch: ['k3d-isolated-session.spec.js', 'k3d-isolated-outage.spec.js'],
}
