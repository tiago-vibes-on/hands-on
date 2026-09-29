import { execFile } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { promisify } from 'node:util'

const executeFile = promisify(execFile)
const endpoint = 'http://127.0.0.1:4566/'
const host = 'floci'
const network = 'hero-association-floci_default'
const postgresImage = 'postgres:18.6-alpine3.24'
const artifactRoot = path.join(path.dirname(fileURLToPath(import.meta.url)), 'artifacts')

async function docker(...args) {
  const { stdout } = await executeFile('docker', args)
  return stdout.trim()
}

async function requireDockerAccess() {
  const mounts = JSON.parse(await docker('inspect', '--format', '{{json .Mounts}}', 'hero-association-floci-floci-1'))
  if (!mounts.some((mount) => mount.Destination === '/var/run/docker.sock')) {
    throw new Error('Start Floci with compose.floci.ecs.yaml before provisioning a real RDS instance')
  }
}

async function rds(action, parameters = {}, allowMissing = false) {
  const body = new URLSearchParams({ Action: action, Version: '2014-10-31', ...parameters })
  const response = await fetch(endpoint, { method: 'POST', body })
  const xml = await response.text()
  if (allowMissing && response.status === 404 && xml.includes('<Code>DBInstanceNotFound</Code>')) return null
  if (!response.ok) throw new Error(`Floci RDS ${action} returned HTTP ${response.status}: ${xml}`)
  return xml
}

function field(xml, name) {
  const matches = [...xml.matchAll(new RegExp(`<${name}>([^<]*)</${name}>`, 'g'))]
  if (matches.length !== 1) throw new Error(`Floci RDS response is missing exactly one ${name}`)
  return matches[0][1]
}

function verifyMetadata(xml, { instanceId, database, username, port }) {
  const expected = {
    DBInstanceIdentifier: instanceId,
    DBInstanceStatus: 'available',
    Engine: 'postgres',
    EngineVersion: '18.6',
    MasterUsername: username,
    DBName: database,
    Address: host,
    Port: port,
    PubliclyAccessible: 'false',
  }
  for (const [name, value] of Object.entries(expected)) {
    if (field(xml, name) !== value) throw new Error(`Floci RDS ${name} differs from the expected ${value}`)
  }
}

async function savedPassword(secretFile, instanceId) {
  try {
    const metadata = await stat(secretFile)
    if ((metadata.mode & 0o077) !== 0) throw new Error(`${secretFile} must be accessible only to its owner`)
    const record = JSON.parse(await readFile(secretFile, 'utf8'))
    if (record.instanceId !== instanceId || typeof record.password !== 'string' || !record.password) {
      throw new Error(`Invalid credential record in ${secretFile}`)
    }
    return record.password
  } catch (error) {
    if (error.code === 'ENOENT') return null
    throw error
  }
}

async function savePassword(secretFile, instanceId, password) {
  await mkdir(artifactRoot, { recursive: true })
  await writeFile(secretFile, `${JSON.stringify({ instanceId, password })}\n`, { flag: 'wx', mode: 0o600 })
}

async function verifyConnection({ secretFile, database, username, port }, password) {
  for (let attempt = 0; attempt < 15; attempt += 1) {
    try {
      const { stdout } = await executeFile('docker', [
        'run', '--rm', '--network', network, '-e', 'PGPASSWORD', postgresImage,
        'psql', '-h', host, '-p', port, '-U', username, '-d', database,
        '-Atc', 'SELECT current_database(), current_user',
      ], { env: { ...process.env, PGPASSWORD: password } })
      if (stdout.trim() !== `${database}|${username}`) throw new Error('PostgreSQL returned unexpected identity')
      return
    } catch (error) {
      if (String(error.stderr ?? '').includes('password authentication failed')) {
        throw new Error(`The password in ${secretFile} does not match the RDS instance`)
      }
      if (attempt === 14) throw error
      await new Promise((resolve) => setTimeout(resolve, 1_000))
    }
  }
}

export async function provisionFlociRds({ label, instanceId, database, username, port, secretFileName, passwordEnvironmentVariable }) {
  const secretFile = path.join(artifactRoot, secretFileName)
  await requireDockerAccess()
  const existing = await rds('DescribeDBInstances', { DBInstanceIdentifier: instanceId }, true)
  const saved = await savedPassword(secretFile, instanceId)
  const supplied = process.env[passwordEnvironmentVariable] || null
  if (saved && supplied && saved !== supplied) {
    throw new Error(`${passwordEnvironmentVariable} differs from the saved lab credential`)
  }
  if (existing && !saved && !supplied) {
    throw new Error(`RDS instance already exists; provide ${passwordEnvironmentVariable} once to save its credential in ${secretFile}`)
  }
  const password = saved ?? supplied ?? randomBytes(24).toString('hex')
  if (!existing) {
    if (!saved) await savePassword(secretFile, instanceId, password)
    await rds('CreateDBInstance', {
      DBInstanceIdentifier: instanceId,
      DBInstanceClass: 'db.t3.micro',
      Engine: 'postgres',
      EngineVersion: '18.6',
      MasterUsername: username,
      MasterUserPassword: password,
      DBName: database,
      AllocatedStorage: '20',
      Port: port,
      PubliclyAccessible: 'false',
    })
  }
  if (existing && field(existing, 'DBInstanceStatus') === 'stopped') {
    throw new Error('The pinned Floci image cannot start a stopped RDS instance; do not stop this lab database')
  }
  let described
  for (let attempt = 0; attempt < 20; attempt += 1) {
    described = await rds('DescribeDBInstances', { DBInstanceIdentifier: instanceId })
    const status = field(described, 'DBInstanceStatus')
    if (status === 'available') break
    if (status !== 'starting' || attempt === 19) {
      throw new Error(`Floci ${label} RDS did not become available: ${status}`)
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  verifyMetadata(described, { instanceId, database, username, port })
  await verifyConnection({ secretFile, database, username, port }, password)
  if (existing && !saved) await savePassword(secretFile, instanceId, password)
  console.log(`Floci ${label} RDS is ready at jdbc:postgresql://${host}:${port}/${database}`)
  console.log(`Local-only credential record: ${secretFile}`)
}
