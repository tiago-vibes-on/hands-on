import base from './playwright.k3d.config.js'
export default { ...base, testMatch: ['quest-dungeon.spec.js'], timeout: 300_000 }
