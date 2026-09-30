// One-time topology and ACL setup. Runtime services never declare broker resources.
const host = process.env.RABBITMQ_SETUP_HOST ?? 'rabbitmq-expedition'
const admin = process.env.RABBITMQ_SETUP_ADMIN_USER ?? 'hero_association_expedition'
const adminPassword = process.env.RABBITMQ_SETUP_ADMIN_PASSWORD
const expeditionPassword = process.env.RABBITMQ_SETUP_EXPEDITION_PASSWORD
const corePassword = process.env.RABBITMQ_SETUP_CORE_PASSWORD

if (!adminPassword || !expeditionPassword || !corePassword) {
  throw new Error('Broker setup requires admin, Expedition, and Core passwords')
}

const base = `http://${host}:15672/api`
const authorization = `Basic ${Buffer.from(`${admin}:${adminPassword}`).toString('base64')}`

for (let attempt = 0; attempt < 30; attempt++) {
  try {
    const response = await fetch(`${base}/overview`, {
      headers: { Authorization: authorization },
    })
    if (response.ok) break
    if (response.status === 401 || response.status === 403) {
      throw new Error(`RabbitMQ setup credentials rejected: HTTP ${response.status}`)
    }
  } catch (error) {
    if (error.message?.startsWith('RabbitMQ setup credentials rejected')) throw error
  }
  if (attempt === 29) throw new Error('RabbitMQ management API did not become ready')
  await new Promise((resolve) => setTimeout(resolve, 1000))
}

async function put(path, body) {
  const response = await fetch(`${base}${path}`, {
    method: 'PUT',
    headers: { Authorization: authorization, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw new Error(`RabbitMQ setup ${path}: HTTP ${response.status}`)
}

async function bind(exchange, queue, routingKey) {
  const path = `/bindings/%2F/e/${exchange}/q/${queue}`
  const response = await fetch(`${base}${path}`, {
    method: 'POST',
    headers: { Authorization: authorization, 'Content-Type': 'application/json' },
    body: JSON.stringify({ routing_key: routingKey, arguments: {} }),
  })
  if (!response.ok) throw new Error(`RabbitMQ setup ${path}: HTTP ${response.status}`)
}

const settlementExchange = 'hero-association.expedition.settlement.v1'
const settlementQueue = 'hero-association.core.expedition-settlement.v1'
const ackExchange = 'hero-association.core.expedition-ack.v1'
const ackQueue = 'hero-association.expedition.settlement-ack.v1'

for (const exchange of [settlementExchange, ackExchange]) {
  await put(`/exchanges/%2F/${exchange}`, {
    type: 'direct', durable: true, auto_delete: false, internal: false, arguments: {},
  })
}
for (const queue of [settlementQueue, ackQueue]) {
  await put(`/queues/%2F/${queue}`, {
    durable: true, auto_delete: false, arguments: {},
  })
}
await bind(settlementExchange, settlementQueue, 'core.apply')
await bind(ackExchange, ackQueue, 'expedition.applied')

for (const [user, password, write, read] of [
  ['hero_association_expedition_worker', expeditionPassword, settlementExchange, ackQueue],
  ['hero_association_core_settlement', corePassword, ackExchange, settlementQueue],
]) {
  await put(`/users/${user}`, { password, tags: '' })
  await put(`/permissions/%2F/${user}`, {
    configure: '^$',
    write: `^${write.replaceAll('.', '\\.')}$`,
    read: `^${read.replaceAll('.', '\\.')}$`,
  })
}

console.log('Expedition broker topology and service permissions are ready')
