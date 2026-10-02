import base from './playwright.k3d.isolated.config.js'

export default { ...base, testMatch: 'k3d-assets-recovery.spec.js', timeout: 180_000 }
