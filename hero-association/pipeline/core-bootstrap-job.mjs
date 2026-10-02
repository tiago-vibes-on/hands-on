export function prepareCoreBootstrapJob(template, imageRef) {
  const job = structuredClone(template)
  const pod = job.spec?.template?.spec
  const bootstrap = pod?.containers?.find((container) => container.name === 'bootstrap')
  const environment = new Map(bootstrap?.env?.map((entry) => [entry.name, entry]) ?? [])
  const passwordSecret = environment.get('QUARKUS_DATASOURCE_PASSWORD')?.valueFrom?.secretKeyRef
  if (job.kind !== 'Job' || job.metadata?.namespace !== 'hero-association' ||
      job.metadata?.generateName !== 'core-db-bootstrap-' ||
      pod?.containers?.length !== 1 || pod.automountServiceAccountToken !== false ||
      bootstrap?.imagePullPolicy !== 'Never' ||
      environment.get('QUARKUS_DATASOURCE_JDBC_URL')?.value !==
        'jdbc:postgresql://postgres-core:5432/hero_association' ||
      environment.get('QUARKUS_DATASOURCE_USERNAME')?.value !== 'hero_association' ||
      passwordSecret?.name !== 'hero-association-k3d-credentials' ||
      passwordSecret?.key !== 'CORE_DATABASE_PASSWORD' ||
      environment.get('QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY')?.value !== 'drop-and-create' ||
      environment.get('QUARKUS_HIBERNATE_ORM_SQL_LOAD_SCRIPT')?.value !== 'import.sql' ||
      environment.get('HERO_ASSOCIATION_CORE_BOOTSTRAP_MODE')?.value !== 'true' ||
      !/^hero-association-core:[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/.test(imageRef)) {
    throw new Error('Core bootstrap Job or archived Core image is not the expected isolated k3d target')
  }
  bootstrap.image = imageRef
  return job
}

export function assertCoreDatabaseIdentity(identity) {
  if (identity !== 'hero_association|hero_association') {
    throw new Error('Refusing to reset an unexpected Core database: ' + identity)
  }
}

export function assertNoActiveExpeditions(unsettledReservations, activeKeys) {
  if (unsettledReservations !== '0' || activeKeys.trim() !== '') {
    throw new Error('Return all active Expeditions and wait for acknowledged settlement before resetting Core and Market')
  }
}
