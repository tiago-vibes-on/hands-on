import assert from 'node:assert/strict'
import { execFile } from 'node:child_process'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const executeFile = promisify(execFile)
const directory = path.dirname(fileURLToPath(import.meta.url))
const generator = path.join(directory, 'generate-realm.mjs')
const source = path.join(directory, '../../backend/keycloak/realm/hero-association-realm.json')

for (const [name, origin, optionalArguments] of [
  ['daily', 'https://heroassociation.test:8443', []],
  ['isolated E2E', 'https://app.e2e.heroassociation.test', ['https://app.e2e.heroassociation.test']],
]) {
  test(`${name} realm uses only its own callback origin`, async () => {
    const temporaryDirectory = await mkdtemp(path.join(tmpdir(), 'hero-association-realm-test-'))
    try {
      const target = path.join(temporaryDirectory, 'realm.json')
      await executeFile(process.execPath, [generator, source, target, ...optionalArguments])
      const realm = JSON.parse(await readFile(target, 'utf8'))
      const bff = realm.clients.find((client) => client.clientId === 'hero-association-bff')
      assert.deepEqual(bff.webOrigins, [origin])
      assert.deepEqual(bff.redirectUris, [
        `${origin}/auth/callback`,
        `${origin}/auth/post-logout`,
      ])
    } finally {
      await rm(temporaryDirectory, { recursive: true, force: true })
    }
  })
}

test('realm generator rejects a callback origin with a path', async () => {
  const temporaryDirectory = await mkdtemp(path.join(tmpdir(), 'hero-association-realm-test-'))
  try {
    await assert.rejects(executeFile(process.execPath, [
      generator, source, path.join(temporaryDirectory, 'realm.json'),
      'https://app.e2e.heroassociation.test/unsafe',
    ]))
  } finally {
    await rm(temporaryDirectory, { recursive: true, force: true })
  }
})
