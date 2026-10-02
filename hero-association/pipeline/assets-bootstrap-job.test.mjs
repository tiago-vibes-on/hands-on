import assert from 'node:assert/strict'
import { test } from 'node:test'

import { assertAssetsDatabaseIdentity, prepareAssetsBootstrapJob } from './assets-bootstrap-job.mjs'

function template() {
  return {
    kind: 'Job',
    metadata: { namespace: 'hero-association', generateName: 'assets-db-bootstrap-' },
    spec: { template: { spec: {
      automountServiceAccountToken: false,
      containers: [{
        name: 'bootstrap',
        image: 'hero-association-assets:k3d',
        imagePullPolicy: 'Never',
        env: [
          { name: 'QUARKUS_DATASOURCE_JDBC_URL', value: 'jdbc:postgresql://postgres-assets:5432/hero_association_assets' },
          { name: 'QUARKUS_DATASOURCE_USERNAME', value: 'hero_association_assets' },
          { name: 'QUARKUS_DATASOURCE_PASSWORD', valueFrom: { secretKeyRef: {
            name: 'hero-association-assets-credentials', key: 'ASSETS_DATABASE_PASSWORD',
          } } },
          { name: 'QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY', value: 'drop-and-create' },
          { name: 'QUARKUS_HIBERNATE_ORM_SQL_LOAD_SCRIPT', value: 'import.sql' },
          { name: 'HERO_ASSOCIATION_ASSETS_BOOTSTRAP_MODE', value: 'true' },
        ],
      }],
    } } },
  }
}

test('uses the verified archive Assets image without changing the source Job', () => {
  const source = template()
  const job = prepareAssetsBootstrapJob(source, 'hero-association-assets:local-20260929-demo')
  assert.equal(job.spec.template.spec.containers[0].image, 'hero-association-assets:local-20260929-demo')
  assert.equal(source.spec.template.spec.containers[0].image, 'hero-association-assets:k3d')
})

test('rejects a different namespace, pull policy, or image repository', () => {
  const wrongNamespace = template()
  wrongNamespace.metadata.namespace = 'default'
  assert.throws(() => prepareAssetsBootstrapJob(wrongNamespace, 'hero-association-assets:demo'))
  const pullable = template()
  pullable.spec.template.spec.containers[0].imagePullPolicy = 'Always'
  assert.throws(() => prepareAssetsBootstrapJob(pullable, 'hero-association-assets:demo'))
  assert.throws(() => prepareAssetsBootstrapJob(template(), 'unrelated-assets:demo'))
  const wrongDatabase = template()
  wrongDatabase.spec.template.spec.containers[0].env[0].value =
    'jdbc:postgresql://postgres-keycloak:5432/keycloak'
  assert.throws(() => prepareAssetsBootstrapJob(wrongDatabase, 'hero-association-assets:demo'))
  const wrongSecret = template()
  wrongSecret.spec.template.spec.containers[0].env[2].valueFrom.secretKeyRef.key =
    'KEYCLOAK_DATABASE_PASSWORD'
  assert.throws(() => prepareAssetsBootstrapJob(wrongSecret, 'hero-association-assets:demo'))
})

test('accepts only the isolated Assets PostgreSQL database identity', () => {
  assert.doesNotThrow(() => assertAssetsDatabaseIdentity('hero_association_assets|hero_association_assets'))
  assert.throws(() => assertAssetsDatabaseIdentity('keycloak|keycloak'))
  assert.throws(() => assertAssetsDatabaseIdentity('postgres|postgres'))
})
