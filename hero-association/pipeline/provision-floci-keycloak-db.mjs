#!/usr/bin/env node

import { provisionFlociRds } from './floci-rds.mjs'

if (process.argv.length !== 2) throw new Error('Usage: node provision-floci-keycloak-db.mjs')

provisionFlociRds({
  label: 'Keycloak',
  instanceId: 'hero-association-keycloak-lab',
  database: 'keycloak',
  username: 'keycloak',
  port: '7002',
  secretFileName: 'floci-keycloak-db.json',
  passwordEnvironmentVariable: 'FLOCI_KEYCLOAK_DB_PASSWORD',
}).catch((error) => {
  console.error(error)
  process.exitCode = 1
})
