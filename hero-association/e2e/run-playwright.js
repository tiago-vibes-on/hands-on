import { spawn } from 'node:child_process'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

import globalSetup from './global-setup.js'
import { prepareArchive, recordE2EVerification } from './archive-images.js'

const e2eDirectory = path.dirname(fileURLToPath(import.meta.url))
const packageLock = JSON.parse(await readFile(path.join(e2eDirectory, 'package-lock.json'), 'utf8'))
const playwrightVersion = packageLock.packages['node_modules/@playwright/test'].version
const playwrightImage = `mcr.microsoft.com/playwright:v${playwrightVersion}-noble`

function run(command, argumentsForCommand, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, argumentsForCommand, {
      ...options,
      stdio: 'inherit',
    })

    child.once('error', reject)
    child.once('exit', (code, signal) => {
      if (code === 0) {
        resolve()
        return
      }
      reject(new Error(`${command} exited with ${signal ?? `code ${code}`}`))
    })
  })
}

const argumentsForPlaywright = process.argv.slice(2)
let archive = null
if (argumentsForPlaywright[0] === '--archive') {
  if (argumentsForPlaywright.length !== 2) {
    throw new Error('Usage: npm run test:archive -- ../pipeline/artifacts/<build-id>/all')
  }
  archive = await prepareArchive(argumentsForPlaywright[1])
  await recordE2EVerification(archive, 'pending')
  argumentsForPlaywright.length = 0
}
const teardown = await globalSetup({ archive })

try {
  await run('docker', [
    'run',
    '--rm',
    '--init',
    '--network', 'hero-association-e2e_default',
    '--add-host', 'host.docker.internal:host-gateway',
    '--ipc', 'host',
    '--user', `${process.getuid()}:${process.getgid()}`,
    '--env', 'E2E_EXTERNAL_STACK=true',
    '--env', 'HERO_ASSOCIATION_E2E_EXPEDITION=' + (process.env.HERO_ASSOCIATION_E2E_EXPEDITION ?? 'false'),
    '--volume', `${e2eDirectory}:/work`,
    '--workdir', '/work',
    playwrightImage,
    'npx', 'playwright', 'test',
    ...argumentsForPlaywright,
  ])
} finally {
  await teardown()
}
if (archive) {
  await recordE2EVerification(archive, 'passed')
  console.log(`Archive E2E verified: ${archive.buildId} (${archive.archiveSha256})`)
}
