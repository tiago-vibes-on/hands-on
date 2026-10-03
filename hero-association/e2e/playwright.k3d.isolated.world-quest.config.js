import base from './playwright.k3d.isolated.config.js'

export default { ...base, testMatch: 'k3d-world-quest-recovery.spec.js', timeout: 180_000, use: { ...base.use, actionTimeout: 15_000 } }
