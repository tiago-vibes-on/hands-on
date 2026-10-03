import assert from 'node:assert/strict'
import { test } from 'node:test'

import { assertWorldDatabaseIdentity, prepareWorldBootstrapJob } from './world-bootstrap-job.mjs'

function template() {
  return {
    kind: 'Job',
    metadata: { namespace: 'hero-association', generateName: 'world-db-bootstrap-' },
    spec: { template: { spec: {
      automountServiceAccountToken: false,
      containers: [{
        name: 'bootstrap',
        image: 'hero-association-world:k3d',
        imagePullPolicy: 'Never',
        env: [
          { name: 'QUARKUS_DATASOURCE_JDBC_URL', value: 'jdbc:postgresql://postgres-world:5432/hero_association_world' },
          { name: 'QUARKUS_DATASOURCE_USERNAME', value: 'hero_association_world' },
          { name: 'QUARKUS_DATASOURCE_PASSWORD', valueFrom: { secretKeyRef: {
            name: 'hero-association-world-credentials', key: 'WORLD_DATABASE_PASSWORD',
          } } },
          { name: 'QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY', value: 'drop-and-create' },
          { name: 'QUARKUS_HIBERNATE_ORM_SQL_LOAD_SCRIPT', value: 'import.sql' },
          { name: 'HERO_ASSOCIATION_WORLD_BOOTSTRAP_MODE', value: 'true' },
        ],
      }],
    } } },
  }
}

test('uses the verified archive World image without changing the source Job', () => {
  const source = template()
  const job = prepareWorldBootstrapJob(source, 'hero-association-world:local-20260929-demo')
  assert.equal(job.spec.template.spec.containers[0].image, 'hero-association-world:local-20260929-demo')
  assert.equal(source.spec.template.spec.containers[0].image, 'hero-association-world:k3d')
})

test('rejects a different namespace, pull policy, or image repository', () => {
  const wrongNamespace = template()
  wrongNamespace.metadata.namespace = 'default'
  assert.throws(() => prepareWorldBootstrapJob(wrongNamespace, 'hero-association-world:demo'))
  const pullable = template()
  pullable.spec.template.spec.containers[0].imagePullPolicy = 'Always'
  assert.throws(() => prepareWorldBootstrapJob(pullable, 'hero-association-world:demo'))
  assert.throws(() => prepareWorldBootstrapJob(template(), 'unrelated-world:demo'))
  const wrongDatabase = template()
  wrongDatabase.spec.template.spec.containers[0].env[0].value =
    'jdbc:postgresql://postgres-keycloak:5432/keycloak'
  assert.throws(() => prepareWorldBootstrapJob(wrongDatabase, 'hero-association-world:demo'))
  const wrongSecret = template()
  wrongSecret.spec.template.spec.containers[0].env[2].valueFrom.secretKeyRef.key =
    'KEYCLOAK_DATABASE_PASSWORD'
  assert.throws(() => prepareWorldBootstrapJob(wrongSecret, 'hero-association-world:demo'))
})

test('accepts only the isolated World PostgreSQL database identity', () => {
  assert.doesNotThrow(() => assertWorldDatabaseIdentity('hero_association_world|hero_association_world'))
  assert.throws(() => assertWorldDatabaseIdentity('keycloak|keycloak'))
  assert.throws(() => assertWorldDatabaseIdentity('postgres|postgres'))
})
