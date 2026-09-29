#!/usr/bin/env node

import { execFile } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { promisify } from 'node:util'

const executeFile = promisify(execFile)
const endpoint = 'http://127.0.0.1:4566/'
const clusterId = 'hero-association-bff-lab'
const description = 'Hero Association BFF session cache in the isolated Floci lab'
const host = 'floci'
const port = '6379'
const network = 'hero-association-floci_default'
const clientImage = 'redis:8.4.1-alpine'

async function requireDockerAccess() {
  const { stdout } = await executeFile('docker', [
    'inspect', '--format', '{{json .Mounts}}', 'hero-association-floci-floci-1',
  ])
  const mounts = JSON.parse(stdout)
  if (!mounts.some((mount) => mount.Destination === '/var/run/docker.sock')) {
    throw new Error('Start Floci with compose.floci.ecs.yaml before provisioning a real cache')
  }
}

async function elasticache(action, parameters = {}, allowMissing = false) {
  const body = new URLSearchParams({ Action: action, Version: '2015-02-02', ...parameters })
  const response = await fetch(endpoint, { method: 'POST', body })
  const xml = await response.text()
  if (allowMissing && response.status === 404 && xml.includes('<Code>ReplicationGroupNotFoundFault</Code>')) return null
  if (!response.ok) throw new Error(`Floci ElastiCache ${action} returned HTTP ${response.status}: ${xml}`)
  return xml
}

function field(xml, name) {
  const matches = [...xml.matchAll(new RegExp(`<${name}>([^<]*)</${name}>`, 'g'))]
  if (matches.length !== 1) throw new Error(`Floci ElastiCache response is missing exactly one ${name}`)
  return matches[0][1]
}

function groupStatus(xml) {
  const status = xml.match(/<ReplicationGroup>[\s\S]*?<Status>([^<]*)<\/Status>/)?.[1]
  if (!status) throw new Error('Floci ElastiCache response has no replication group status')
  return status
}

function verifyOwnership(xml) {
  if (field(xml, 'ReplicationGroupId') !== clusterId || field(xml, 'Description') !== description ||
      field(xml, 'Engine') !== 'redis' || field(xml, 'AuthTokenEnabled') !== 'false') {
    throw new Error('Existing cache does not match this disposable BFF lab resource')
  }
}

function verifyMetadata(xml) {
  verifyOwnership(xml)
  if (groupStatus(xml) !== 'available') throw new Error('BFF cache is not available')
  const primary = xml.match(/<PrimaryEndpoint>([\s\S]*?)<\/PrimaryEndpoint>/)?.[1]
  if (!primary || field(primary, 'Address') !== host || field(primary, 'Port') !== port) {
    throw new Error('BFF cache primary endpoint is not the private Floci network address')
  }
}

async function redis(...args) {
  const { stdout } = await executeFile('docker', [
    'run', '--rm', '--network', network, clientImage,
    'redis-cli', '-h', host, '-p', port, '--raw', ...args,
  ])
  return stdout.trim()
}

async function verifyConnection(maxAttempts = 15) {
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    try {
      if (await redis('PING') !== 'PONG') throw new Error('BFF cache did not respond to PING')
      const key = `hero-association:lab:probe:${randomBytes(8).toString('hex')}`
      const value = randomBytes(8).toString('hex')
      if (await redis('SET', key, value, 'EX', '30') !== 'OK') throw new Error('BFF cache did not accept SET')
      if (await redis('GET', key) !== value) throw new Error('BFF cache did not return the stored value')
      await redis('DEL', key)
      return
    } catch (error) {
      if (attempt === maxAttempts - 1) throw error
      await new Promise((resolve) => setTimeout(resolve, 1_000))
    }
  }
}

async function describeReady() {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const described = await elasticache('DescribeReplicationGroups', { ReplicationGroupId: clusterId })
    const status = groupStatus(described)
    if (status === 'available') return described
    if (status !== 'creating' || attempt === 29) throw new Error(`Floci BFF cache did not become available: ${status}`)
    await new Promise((resolve) => setTimeout(resolve, 1_000))
  }
}

async function createCache() {
  await elasticache('CreateReplicationGroup', {
    ReplicationGroupId: clusterId,
    ReplicationGroupDescription: description,
    Engine: 'redis',
    NumCacheClusters: '1',
  })
  verifyMetadata(await describeReady())
  await verifyConnection()
}

async function main() {
  const recreateEmpty = process.argv.length === 3 && process.argv[2] === '--recreate-empty'
  if (process.argv.length !== 2 && !recreateEmpty) {
    throw new Error('Usage: node provision-floci-bff-cache.mjs [--recreate-empty]')
  }
  await requireDockerAccess()
  const existing = await elasticache('DescribeReplicationGroups', { ReplicationGroupId: clusterId }, true)
  if (existing) {
    verifyOwnership(existing)
    try {
      verifyMetadata(await describeReady())
      await verifyConnection(3)
      console.log(`Floci BFF cache is ready at redis://${host}:${port}`)
      return
    } catch (error) {
      if (!recreateEmpty) {
        throw new Error('BFF cache metadata exists but its data plane is unavailable; rerun with --recreate-empty to delete and recreate this disposable cache (all sessions are lost)', { cause: error })
      }
      await elasticache('DeleteReplicationGroup', { ReplicationGroupId: clusterId })
      for (let attempt = 0; attempt < 20; attempt += 1) {
        if (!await elasticache('DescribeReplicationGroups', { ReplicationGroupId: clusterId }, true)) break
        if (attempt === 19) throw new Error('Timed out deleting the disposable BFF cache')
        await new Promise((resolve) => setTimeout(resolve, 500))
      }
      console.log('Recreating the disposable BFF cache; previous session data is gone')
    }
  }
  await createCache()
  console.log(`Floci BFF cache is ready at redis://${host}:${port}`)
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
