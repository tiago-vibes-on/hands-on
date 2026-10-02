import assert from 'node:assert/strict'
import { test } from 'node:test'

import { assertMarketDatabaseIdentity, prepareMarketBootstrapJob } from './market-bootstrap-job.mjs'

function template() {
  return {
    kind: 'Job',
    metadata: { namespace: 'hero-association', generateName: 'market-db-bootstrap-' },
    spec: { template: { spec: {
      automountServiceAccountToken: false,
      containers: [{
        name: 'bootstrap',
        image: 'hero-association-market:k3d',
        imagePullPolicy: 'Never',
        env: [
          { name: 'QUARKUS_DATASOURCE_JDBC_URL', value: 'jdbc:postgresql://postgres-market:5432/hero_association_market' },
          { name: 'QUARKUS_DATASOURCE_USERNAME', value: 'hero_association_market' },
          { name: 'QUARKUS_DATASOURCE_PASSWORD', valueFrom: { secretKeyRef: {
            name: 'hero-association-market-credentials', key: 'MARKET_DATABASE_PASSWORD',
          } } },
          { name: 'QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY', value: 'drop-and-create' },
          { name: 'QUARKUS_HIBERNATE_ORM_SQL_LOAD_SCRIPT', value: 'import.sql' },
          { name: 'HERO_ASSOCIATION_MARKET_BOOTSTRAP_MODE', value: 'true' },
        ],
      }],
    } } },
  }
}

test('uses the verified archive Market image without changing the source Job', () => {
  const source = template()
  const job = prepareMarketBootstrapJob(source, 'hero-association-market:local-20260929-demo')
  assert.equal(job.spec.template.spec.containers[0].image, 'hero-association-market:local-20260929-demo')
  assert.equal(source.spec.template.spec.containers[0].image, 'hero-association-market:k3d')
})

test('rejects a different namespace, pull policy, or image repository', () => {
  const wrongNamespace = template()
  wrongNamespace.metadata.namespace = 'default'
  assert.throws(() => prepareMarketBootstrapJob(wrongNamespace, 'hero-association-market:demo'))
  const pullable = template()
  pullable.spec.template.spec.containers[0].imagePullPolicy = 'Always'
  assert.throws(() => prepareMarketBootstrapJob(pullable, 'hero-association-market:demo'))
  assert.throws(() => prepareMarketBootstrapJob(template(), 'unrelated-market:demo'))
  const wrongDatabase = template()
  wrongDatabase.spec.template.spec.containers[0].env[0].value =
    'jdbc:postgresql://postgres-keycloak:5432/keycloak'
  assert.throws(() => prepareMarketBootstrapJob(wrongDatabase, 'hero-association-market:demo'))
  const wrongSecret = template()
  wrongSecret.spec.template.spec.containers[0].env[2].valueFrom.secretKeyRef.key =
    'KEYCLOAK_DATABASE_PASSWORD'
  assert.throws(() => prepareMarketBootstrapJob(wrongSecret, 'hero-association-market:demo'))
})

test('accepts only the isolated Market PostgreSQL database identity', () => {
  assert.doesNotThrow(() => assertMarketDatabaseIdentity('hero_association_market|hero_association_market'))
  assert.throws(() => assertMarketDatabaseIdentity('keycloak|keycloak'))
  assert.throws(() => assertMarketDatabaseIdentity('postgres|postgres'))
})
