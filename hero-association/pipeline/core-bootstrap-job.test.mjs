import assert from 'node:assert/strict'
import { test } from 'node:test'

import { assertCoreDatabaseIdentity, prepareCoreBootstrapJob, assertNoActiveExpeditions } from './core-bootstrap-job.mjs'

function template() {
  return {
    kind: 'Job',
    metadata: { namespace: 'hero-association', generateName: 'core-db-bootstrap-' },
    spec: { template: { spec: {
      automountServiceAccountToken: false,
      containers: [{
        name: 'bootstrap',
        image: 'hero-association-core:k3d',
        imagePullPolicy: 'Never',
        env: [
          { name: 'QUARKUS_DATASOURCE_JDBC_URL', value: 'jdbc:postgresql://postgres-core:5432/hero_association' },
          { name: 'QUARKUS_DATASOURCE_USERNAME', value: 'hero_association' },
          { name: 'QUARKUS_DATASOURCE_PASSWORD', valueFrom: { secretKeyRef: {
            name: 'hero-association-k3d-credentials', key: 'CORE_DATABASE_PASSWORD',
          } } },
          { name: 'QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY', value: 'drop-and-create' },
          { name: 'QUARKUS_HIBERNATE_ORM_SQL_LOAD_SCRIPT', value: 'import.sql' },
          { name: 'HERO_ASSOCIATION_CORE_BOOTSTRAP_MODE', value: 'true' },
        ],
      }],
    } } },
  }
}

test('uses the verified archive Core image without changing the source Job', () => {
  const source = template()
  const job = prepareCoreBootstrapJob(source, 'hero-association-core:local-20260929-demo')
  assert.equal(job.spec.template.spec.containers[0].image, 'hero-association-core:local-20260929-demo')
  assert.equal(source.spec.template.spec.containers[0].image, 'hero-association-core:k3d')
})

test('rejects a different namespace, pull policy, or image repository', () => {
  const wrongNamespace = template()
  wrongNamespace.metadata.namespace = 'default'
  assert.throws(() => prepareCoreBootstrapJob(wrongNamespace, 'hero-association-core:demo'))
  const pullable = template()
  pullable.spec.template.spec.containers[0].imagePullPolicy = 'Always'
  assert.throws(() => prepareCoreBootstrapJob(pullable, 'hero-association-core:demo'))
  assert.throws(() => prepareCoreBootstrapJob(template(), 'unrelated-core:demo'))
  const wrongDatabase = template()
  wrongDatabase.spec.template.spec.containers[0].env[0].value =
    'jdbc:postgresql://postgres-keycloak:5432/keycloak'
  assert.throws(() => prepareCoreBootstrapJob(wrongDatabase, 'hero-association-core:demo'))
  const wrongSecret = template()
  wrongSecret.spec.template.spec.containers[0].env[2].valueFrom.secretKeyRef.key =
    'KEYCLOAK_DATABASE_PASSWORD'
  assert.throws(() => prepareCoreBootstrapJob(wrongSecret, 'hero-association-core:demo'))
})

test('accepts only the isolated Core PostgreSQL database identity', () => {
  assert.doesNotThrow(() => assertCoreDatabaseIdentity('hero_association|hero_association'))
  assert.throws(() => assertCoreDatabaseIdentity('keycloak|keycloak'))
  assert.throws(() => assertCoreDatabaseIdentity('postgres|postgres'))
})

test('refuses coupled reset while an Expedition admission or active run remains', () => {
  assert.doesNotThrow(() => assertNoActiveExpeditions('0', ''))
  assert.throws(() => assertNoActiveExpeditions('1', ''), /Return all active Expeditions/)
  assert.throws(() => assertNoActiveExpeditions('0', 'ha:expedition:v1:owner:active'), /Return all active Expeditions/)
})
