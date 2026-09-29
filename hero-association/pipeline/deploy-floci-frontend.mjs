#!/usr/bin/env node

import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { inspectArchive, prepareArchive, requirePassingE2EVerification } from '../e2e/archive-images.js'

const executeFile = promisify(execFile)
const endpoint = 'http://127.0.0.1:4566'
const clusterName = 'hero-association-lab'
const family = 'hero-association-frontend-preview'

async function ecs(action, payload) {
  const response = await fetch(`${endpoint}/`, {
    method: 'POST',
    headers: {
      'content-type': 'application/x-amz-json-1.1',
      'x-amz-target': `AmazonEC2ContainerServiceV20141113.${action}`,
    },
    body: JSON.stringify(payload),
  })
  const body = await response.text()
  if (!response.ok) throw new Error(`Floci ECS ${action} returned HTTP ${response.status}: ${body}`)
  return JSON.parse(body)
}

async function verifyTask(task, image) {
  if (task.lastStatus !== 'RUNNING') throw new Error(`ECS task is ${task.lastStatus}, not RUNNING`)
  const taskId = task.taskArn?.split('/').at(-1)
  if (!/^[a-f0-9]{32}$/.test(taskId)) throw new Error('Unexpected Floci ECS task ARN')
  const containerName = `floci-ecs-${taskId}-frontend`
  const { stdout } = await executeFile('docker', ['container', 'inspect', '--format', '{{.Image}}', containerName])
  if (stdout.trim() !== image.id) throw new Error(`ECS container image differs from ${image.ref}`)

  for (let attempt = 0; attempt < 20; attempt += 1) {
    try {
      const { stdout: html } = await executeFile('docker', [
        'exec', containerName, 'wget', '-qO-', 'http://127.0.0.1/',
      ])
      if (html.includes('<title>Hero Association</title>')) {
        console.log(`Verified ${image.ref} and internal HTTP in ECS task ${task.taskArn}`)
        return
      }
    } catch { // The container may still be starting.
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error('ECS frontend did not serve Hero Association inside its container')
}

async function main() {
  if (process.argv.length !== 3) {
    throw new Error('Usage: node deploy-floci-frontend.mjs artifacts/<downloaded-floci-archive>')
  }
  const archive = await inspectArchive(process.argv[2])
  if (archive.promoteComponent) throw new Error('K3d service archives are not portable Floci artifacts')
  await prepareArchive(archive.archiveDirectory)
  await requirePassingE2EVerification(archive)
  const image = archive.images.frontend

  await ecs('CreateCluster', { clusterName })
  const listed = await ecs('ListTasks', { cluster: clusterName, family, desiredStatus: 'RUNNING' })
  if (listed.taskArns?.length > 1) throw new Error('Multiple frontend preview tasks are running; inspect them before deploying')
  if (listed.taskArns?.length === 1) {
    const described = await ecs('DescribeTasks', { cluster: clusterName, tasks: listed.taskArns })
    const task = described.tasks?.[0]
    const definition = await ecs('DescribeTaskDefinition', { taskDefinition: task.taskDefinitionArn })
    const container = definition.taskDefinition?.containerDefinitions?.[0]
    if (container?.image !== image.ref) {
      throw new Error('A different frontend build is running; stop it explicitly before promoting this build')
    }
    if (definition.taskDefinition.networkMode !== 'bridge' || container.portMappings?.length) {
      throw new Error('The running frontend task publishes a port or uses unexpected networking; stop it explicitly')
    }
    await verifyTask(task, image)
    return
  }

  const registered = await ecs('RegisterTaskDefinition', {
    family,
    networkMode: 'bridge',
    containerDefinitions: [{
      name: 'frontend',
      image: image.ref,
      essential: true,
      cpu: 256,
      memory: 128,
    }],
  })
  const started = await ecs('RunTask', {
    cluster: clusterName,
    taskDefinition: registered.taskDefinition.taskDefinitionArn,
    launchType: 'EC2',
    count: 1,
  })
  if (started.failures?.length || started.tasks?.length !== 1) {
    throw new Error(`Floci ECS failed to start the frontend: ${JSON.stringify(started.failures)}`)
  }
  await verifyTask(started.tasks[0], image)
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
