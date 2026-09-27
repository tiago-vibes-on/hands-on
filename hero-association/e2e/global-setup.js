import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const executeFile = promisify(execFile)
const e2eDirectory = path.dirname(fileURLToPath(import.meta.url))
const projectDirectory = path.resolve(e2eDirectory, '..')
const backendDirectory = path.join(projectDirectory, 'backend')
const composeFiles = [
  path.join(backendDirectory, 'compose.yaml'),
  path.join(e2eDirectory, 'compose.e2e.yaml'),
]

const e2eEnvironment = {
  ...process.env,
  KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME: 'hero-association-e2e-admin',
  KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD: 'hero-association-e2e-admin-password',
  KEYCLOAK_DATABASE_PASSWORD: 'hero-association-e2e-keycloak-database-password',
  HERO_ASSOCIATION_BFF_OIDC_CLIENT_SECRET: 'hero-association-e2e-client-secret-please-do-not-use-in-production',
  HERO_ASSOCIATION_BFF_OIDC_STATE_SECRET: 'hero-association-e2e-state-secret-please-do-not-use-in-production',
  HERO_ASSOCIATION_BFF_CSRF_TOKEN_SIGNATURE_KEY: 'hero-association-e2e-csrf-signing-key-please-do-not-use-in-production',
}

const keycloakUrl = 'http://localhost:18180'
const e2eTokenLifespanSeconds = 8

function composeArguments(...argumentsAfterCompose) {
  return [
    'compose',
    '--project-name', 'hero-association-e2e',
    '--project-directory', backendDirectory,
    '-f', composeFiles[0],
    '-f', composeFiles[1],
    ...argumentsAfterCompose,
  ]
}

async function compose(...argumentsAfterCompose) {
  await executeFile('docker', composeArguments(...argumentsAfterCompose), {
    cwd: projectDirectory,
    env: e2eEnvironment,
    maxBuffer: 10 * 1024 * 1024,
  })
}

async function waitForService(url, description) {
  const deadline = Date.now() + 180_000
  let lastError

  while (Date.now() < deadline) {
    try {
      const response = await fetch(url)
      if (response.ok || response.status === 401) {
        return
      }
      lastError = new Error(`received HTTP ${response.status}`)
    } catch (error) {
      lastError = error
    }

    await new Promise((resolve) => setTimeout(resolve, 1_000))
  }

  throw new Error(`Timed out waiting for ${description}: ${lastError?.message ?? 'unknown error'}`)
}

async function configureShortLivedE2ETokens() {
  const parameters = new URLSearchParams({
    grant_type: 'password',
    client_id: 'admin-cli',
    username: e2eEnvironment.KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME,
    password: e2eEnvironment.KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD,
  })
  const tokenResponse = await fetch(`${keycloakUrl}/realms/master/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: parameters,
  })

  if (!tokenResponse.ok) {
    throw new Error(`Unable to authenticate the E2E Keycloak administrator: HTTP ${tokenResponse.status}`)
  }

  const { access_token: accessToken } = await tokenResponse.json()
  const realmResponse = await fetch(`${keycloakUrl}/admin/realms/hero-association`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })

  if (!realmResponse.ok) {
    throw new Error(`Unable to read the E2E Keycloak realm: HTTP ${realmResponse.status}`)
  }

  const realm = await realmResponse.json()
  realm.accessTokenLifespan = e2eTokenLifespanSeconds

  const updateResponse = await fetch(`${keycloakUrl}/admin/realms/hero-association`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(realm),
  })

  if (!updateResponse.ok) {
    throw new Error(`Unable to set the E2E token lifespan: HTTP ${updateResponse.status}`)
  }
}
export default async function globalSetup() {
  await executeFile(path.join(projectDirectory, 'traefik', 'generate-local-certs.sh'))
  await compose('down', '--volumes')

  try {
    await compose('up', '--build', '--detach')
    await waitForService('http://localhost:18180/realms/hero-association/.well-known/openid-configuration', 'Keycloak')
    await configureShortLivedE2ETokens()
    await Promise.all([
      waitForService('http://localhost:18081/api/v1/account', 'Game Core'),
      waitForService('http://localhost:18080/api/v1/session', 'BFF'),
    ])
  } catch (error) {
    await compose('down', '--volumes').catch(() => undefined)
    throw error
  }

  return async () => {
    await compose('down', '--volumes')
  }
}
