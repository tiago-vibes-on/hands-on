import { spawn } from 'node:child_process'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const e2eDirectory = path.dirname(fileURLToPath(import.meta.url))
const packageLock = JSON.parse(await readFile(path.join(e2eDirectory, 'package-lock.json'), 'utf8'))
const playwrightVersion = packageLock.packages['node_modules/@playwright/test'].version
const playwrightImage = `mcr.microsoft.com/playwright:v${playwrightVersion}-noble`
const configFile = process.env.HERO_ASSOCIATION_K3D_PLAYWRIGHT_CONFIG || 'playwright.k3d.config.js'
const k3dNetwork = 'k3d-hero-association'

const argumentsForDocker = [
  'run', '--rm', '--init',
  '--network', k3dNetwork,
  '--add-host', 'heroassociation.test:host-gateway',
  '--add-host', 'auth.heroassociation.test:host-gateway',
  '--ipc', 'host',
  '--user', `${process.getuid()}:${process.getgid()}`,
  '--volume', `${e2eDirectory}:/work`,
  '--workdir', '/work',
  '--env', `HERO_ASSOCIATION_K3D_LOAD_SECONDS=${process.env.HERO_ASSOCIATION_K3D_LOAD_SECONDS || ''}`,
  '--env', `HERO_ASSOCIATION_K3D_LOAD_CLIENTS=${process.env.HERO_ASSOCIATION_K3D_LOAD_CLIENTS || ''}`,
  '--env', `HERO_ASSOCIATION_K3D_MIXED_SECONDS=${process.env.HERO_ASSOCIATION_K3D_MIXED_SECONDS || ''}`,
  '--env', `HERO_ASSOCIATION_K3D_MIXED_CLIENTS=${process.env.HERO_ASSOCIATION_K3D_MIXED_CLIENTS || ''}`,
  '--env', `HERO_ASSOCIATION_K3D_CLEANUP_EXPEDITION_ID=${process.env.HERO_ASSOCIATION_K3D_CLEANUP_EXPEDITION_ID || ''}`,
  playwrightImage,
  'npx', 'playwright', 'test', '--config', configFile,
  ...process.argv.slice(2),
]

const child = spawn('docker', argumentsForDocker, { stdio: 'inherit' })
child.once('error', (error) => {
  console.error(error)
  process.exitCode = 1
})
child.once('exit', (code, signal) => {
  if (signal) {
    console.error(`Playwright container stopped by ${signal}`)
  }
  process.exitCode = code ?? 1
})
