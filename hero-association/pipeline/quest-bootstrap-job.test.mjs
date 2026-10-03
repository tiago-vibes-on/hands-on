import assert from 'node:assert/strict'
import { test } from 'node:test'

import { assertQuestDatabaseIdentity, prepareQuestBootstrapJob } from './quest-bootstrap-job.mjs'

function template() {
  return {
    kind: 'Job',
    metadata: { namespace: 'hero-association', generateName: 'quest-db-bootstrap-' },
    spec: { template: { spec: {
      automountServiceAccountToken: false,
      containers: [{
        name: 'bootstrap',
        image: 'hero-association-quest:k3d',
        imagePullPolicy: 'Never',
        env: [
          { name: 'QUARKUS_DATASOURCE_JDBC_URL', value: 'jdbc:postgresql://postgres-quest:5432/hero_association_quest' },
          { name: 'QUARKUS_DATASOURCE_USERNAME', value: 'hero_association_quest' },
          { name: 'QUARKUS_DATASOURCE_PASSWORD', valueFrom: { secretKeyRef: {
            name: 'hero-association-quest-credentials', key: 'QUEST_DATABASE_PASSWORD',
          } } },
          { name: 'QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY', value: 'drop-and-create' },
          { name: 'QUARKUS_HIBERNATE_ORM_SQL_LOAD_SCRIPT', value: 'import.sql' },
          { name: 'HERO_ASSOCIATION_QUEST_BOOTSTRAP_MODE', value: 'true' },
        ],
      }],
    } } },
  }
}

test('uses the verified archive Quest image without changing the source Job', () => {
  const source = template()
  const job = prepareQuestBootstrapJob(source, 'hero-association-quest:local-20260929-demo')
  assert.equal(job.spec.template.spec.containers[0].image, 'hero-association-quest:local-20260929-demo')
  assert.equal(source.spec.template.spec.containers[0].image, 'hero-association-quest:k3d')
})

test('rejects a different namespace, pull policy, or image repository', () => {
  const wrongNamespace = template()
  wrongNamespace.metadata.namespace = 'default'
  assert.throws(() => prepareQuestBootstrapJob(wrongNamespace, 'hero-association-quest:demo'))
  const pullable = template()
  pullable.spec.template.spec.containers[0].imagePullPolicy = 'Always'
  assert.throws(() => prepareQuestBootstrapJob(pullable, 'hero-association-quest:demo'))
  assert.throws(() => prepareQuestBootstrapJob(template(), 'unrelated-quest:demo'))
  const wrongDatabase = template()
  wrongDatabase.spec.template.spec.containers[0].env[0].value =
    'jdbc:postgresql://postgres-keycloak:5432/keycloak'
  assert.throws(() => prepareQuestBootstrapJob(wrongDatabase, 'hero-association-quest:demo'))
  const wrongSecret = template()
  wrongSecret.spec.template.spec.containers[0].env[2].valueFrom.secretKeyRef.key =
    'KEYCLOAK_DATABASE_PASSWORD'
  assert.throws(() => prepareQuestBootstrapJob(wrongSecret, 'hero-association-quest:demo'))
})

test('accepts only the isolated Quest PostgreSQL database identity', () => {
  assert.doesNotThrow(() => assertQuestDatabaseIdentity('hero_association_quest|hero_association_quest'))
  assert.throws(() => assertQuestDatabaseIdentity('keycloak|keycloak'))
  assert.throws(() => assertQuestDatabaseIdentity('postgres|postgres'))
})
