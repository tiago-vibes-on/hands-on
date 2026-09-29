#!/usr/bin/env node

import { provisionFlociRds } from './floci-rds.mjs'

if (process.argv.length !== 2) throw new Error('Usage: node provision-floci-core-db.mjs')

provisionFlociRds({
  label: 'Core',
  instanceId: 'hero-association-core-lab',
  database: 'hero_association',
  username: 'hero_association',
  port: '7001',
  secretFileName: 'floci-core-db.json',
  passwordEnvironmentVariable: 'FLOCI_CORE_DB_PASSWORD',
}).catch((error) => {
  console.error(error)
  process.exitCode = 1
})
