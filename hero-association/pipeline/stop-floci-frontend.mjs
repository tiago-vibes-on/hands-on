#!/usr/bin/env node

const endpoint = 'http://127.0.0.1:4566'
const cluster = 'hero-association-lab'
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

async function main() {
  if (process.argv.length !== 2) throw new Error('Usage: node stop-floci-frontend.mjs')
  const listed = await ecs('ListTasks', { cluster, family, desiredStatus: 'RUNNING' })
  if (listed.taskArns?.length > 1) throw new Error('Multiple frontend preview tasks found; inspect them before stopping')
  if (!listed.taskArns?.length) {
    console.log('No frontend preview task is running')
    return
  }
  const taskArn = listed.taskArns[0]
  await ecs('StopTask', { cluster, task: taskArn, reason: 'Stop Hero Association Floci frontend preview' })
  console.log(`Stopped ${taskArn}`)
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
