import { spawn } from 'node:child_process'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

import globalSetup from './global-setup.js'

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

const teardown = await globalSetup()

try {
  await run('docker', [
    'run',
    '--rm',
    '--init',
    '--add-host', 'host.docker.internal:host-gateway',
    '--ipc', 'host',
    '--user', `${process.getuid()}:${process.getgid()}`,
    '--env', 'E2E_EXTERNAL_STACK=true',
    '--volume', `${e2eDirectory}:/work`,
    '--workdir', '/work',
    playwrightImage,
    'npx', 'playwright', 'test',
    ...process.argv.slice(2),
  ])
} finally {
  await teardown()
}
