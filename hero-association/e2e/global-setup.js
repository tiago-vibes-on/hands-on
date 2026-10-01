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
const archiveComposeFile = path.join(e2eDirectory, 'compose.archive.yaml')
let expeditionMode = process.env.HERO_ASSOCIATION_E2E_EXPEDITION === 'true'
let activeArchive = null

const e2eEnvironment = {
  ...process.env,
  KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME: 'hero-association-e2e-admin',
  KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD: 'hero-association-e2e-admin-password',
  KEYCLOAK_DATABASE_PASSWORD: 'hero-association-e2e-keycloak-database-password',
  HERO_ASSOCIATION_BFF_OIDC_CLIENT_SECRET: 'hero-association-e2e-client-secret-please-do-not-use-in-production',
  HERO_ASSOCIATION_BFF_OIDC_STATE_SECRET: 'hero-association-e2e-state-secret-please-do-not-use-in-production',
  HERO_ASSOCIATION_BFF_CSRF_TOKEN_SIGNATURE_KEY: 'hero-association-e2e-csrf-signing-key-please-do-not-use-in-production',
  HERO_ASSOCIATION_E2E_EXPEDITION_CORE_SERVICE_KEY: 'e2e-only-expedition-core-service-key-2026',
  HERO_ASSOCIATION_E2E_EXPEDITION_BFF_SERVICE_KEY: 'e2e-only-expedition-bff-service-key-2026',
  HERO_ASSOCIATION_E2E_EXPEDITION_RABBITMQ_PASSWORD: 'e2e-only-expedition-rabbitmq-password',
  HERO_ASSOCIATION_E2E_EXPEDITION_WORKER_RABBITMQ_PASSWORD: 'e2e-only-expedition-worker-rabbitmq-password',
  HERO_ASSOCIATION_E2E_CORE_SETTLEMENT_RABBITMQ_PASSWORD: 'e2e-only-core-settlement-rabbitmq-password',
}

const keycloakUrl = 'http://localhost:18180'
const e2eTokenLifespanSeconds = 8

function composeArguments(...argumentsAfterCompose) {
  return [
    'compose',
    '--project-name', 'hero-association-e2e',
    '--project-directory', backendDirectory,
    ...composeFiles.flatMap((file) => ['-f', file]),
    ...(activeArchive ? ['-f', archiveComposeFile] : []),
    ...argumentsAfterCompose,
  ]
}

function composeEnvironment() {
  if (!activeArchive) {
    return e2eEnvironment
  }
  return {
    ...e2eEnvironment,
    HERO_ASSOCIATION_E2E_CORE_IMAGE: activeArchive.images.core.ref,
    HERO_ASSOCIATION_E2E_BFF_IMAGE: activeArchive.images.bff.ref,
    HERO_ASSOCIATION_E2E_EXPEDITION_IMAGE: activeArchive.images.expedition.ref,
    HERO_ASSOCIATION_E2E_FRONTEND_IMAGE: activeArchive.images.frontend.ref,
  }
}

function compose(...argumentsAfterCompose) {
  return executeFile('docker', composeArguments(...argumentsAfterCompose), {
    cwd: projectDirectory,
    env: composeEnvironment(),
    maxBuffer: 10 * 1024 * 1024,
  })
}

async function verifyArchiveCompose() {
  const { stdout } = await compose('config', '--format', 'json')
  const services = JSON.parse(stdout).services
  for (const [service, component] of Object.entries({
    core: 'core',
    bff: 'bff',
    'bff-secondary': 'bff',
    ...(expeditionMode ? { expedition: 'expedition' } : {}),
    frontend: 'frontend',
  })) {
    const definition = services[service]
    if (definition?.build !== undefined ||
        definition?.image !== activeArchive.images[component].ref ||
        definition?.pull_policy !== 'never') {
      throw new Error(`Archive E2E Compose service ${service} must use its archived image without a build`)
    }
  }
}

async function verifyRunningImages() {
  for (const [service, component] of Object.entries({
    core: 'core',
    bff: 'bff',
    'bff-secondary': 'bff',
    ...(expeditionMode ? { expedition: 'expedition' } : {}),
    frontend: 'frontend',
  })) {
    const { stdout } = await compose('ps', '-q', service)
    const containerId = stdout.trim()
    if (!containerId || containerId.includes('\n')) {
      throw new Error(`Expected exactly one running ${service} container`)
    }
    const inspected = await executeFile('docker', [
      'inspect', '--format', '{{.Image}}', containerId,
    ])
    if (inspected.stdout.trim() !== activeArchive.images[component].id) {
      throw new Error(`Running ${service} container does not match the archived image ID`)
    }
  }
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
export default async function globalSetup({ archive } = {}) {
  expeditionMode ||= Boolean(archive?.images.expedition)
  if (expeditionMode && !composeFiles.includes(path.join(e2eDirectory, 'compose.expedition.e2e.yaml'))) {
    composeFiles.push(path.join(e2eDirectory, 'compose.expedition.e2e.yaml'))
  }
  activeArchive = archive ?? null
  if (activeArchive) {
    await verifyArchiveCompose()
  }
  await executeFile(path.join(projectDirectory, 'traefik', 'generate-local-certs.sh'))
  await compose('down', '--volumes')

  try {
    await compose('up', activeArchive ? '--no-build' : '--build', '--detach')
    await waitForService('http://localhost:18180/realms/hero-association/.well-known/openid-configuration', 'Keycloak')
    await configureShortLivedE2ETokens()
    await Promise.all([
      waitForService('http://localhost:18081/api/v1/account', 'Game Core'),
      waitForService('http://localhost:18080/api/v1/session', 'BFF'),
      ...(expeditionMode ? [waitForService('http://localhost:18083/q/health/ready', 'Expedition')] : []),
    ])
    if (activeArchive) {
      await verifyRunningImages()
    }
  } catch (error) {
    await compose('down', '--volumes').catch(() => undefined)
    throw error
  }

  return async () => {
    await compose('down', '--volumes')
  }
}
