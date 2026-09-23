import { execFile, spawn } from 'node:child_process'
import { once } from 'node:events'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const executeFile = promisify(execFile)
const e2eDirectory = path.dirname(fileURLToPath(import.meta.url))
const projectDirectory = path.resolve(e2eDirectory, '..')
const backendDirectory = path.join(projectDirectory, 'backend')
const frontendDirectory = path.join(projectDirectory, 'frontend')
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
  HERO_ASSOCIATION_BFF_DATABASE_PASSWORD: 'hero-association-e2e-bff-database-password',
}

let frontendProcess

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

async function startFrontend() {
  const viteEntryPoint = path.join(frontendDirectory, 'node_modules', 'vite', 'bin', 'vite.js')

  frontendProcess = spawn(process.execPath, [viteEntryPoint, '--host', '127.0.0.1', '--port', '15173'], {
    cwd: frontendDirectory,
    env: {
      ...process.env,
      VITE_API_PROXY_TARGET: 'http://localhost:18080',
      VITE_API_PROXY_CHANGE_ORIGIN: 'false',
      VITE_ALLOWED_HOSTS: 'host.docker.internal',
    },
    stdio: 'ignore',
  })

  await waitForService('http://127.0.0.1:15173', 'frontend')
}

async function stopFrontend() {
  if (!frontendProcess || frontendProcess.exitCode !== null) {
    return
  }

  const stopped = once(frontendProcess, 'exit')
  frontendProcess.kill('SIGTERM')

  const exited = await Promise.race([
    stopped.then(() => true),
    new Promise((resolve) => setTimeout(() => resolve(false), 5_000)),
  ])

  if (!exited && frontendProcess.exitCode === null) {
    frontendProcess.kill('SIGKILL')
    await stopped
  }
}

export default async function globalSetup() {
  await compose('down', '--volumes')

  try {
    await compose('up', '--build', '--detach')
    await Promise.all([
      waitForService('http://localhost:18180/realms/hero-association/.well-known/openid-configuration', 'Keycloak'),
      waitForService('http://localhost:18081/api/v1/account', 'Game Core'),
      waitForService('http://localhost:18080/api/v1/session', 'BFF'),
    ])
    await startFrontend()
  } catch (error) {
    await stopFrontend().catch(() => undefined)
    await compose('down', '--volumes').catch(() => undefined)
    throw error
  }

  return async () => {
    await stopFrontend()
    await compose('down', '--volumes')
  }
}
