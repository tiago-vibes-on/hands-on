export function prepareWorldBootstrapJob(template, imageRef) {
  const job = structuredClone(template)
  const pod = job.spec?.template?.spec
  const bootstrap = pod?.containers?.find((container) => container.name === 'bootstrap')
  const environment = new Map(bootstrap?.env?.map((entry) => [entry.name, entry]) ?? [])
  const passwordSecret = environment.get('QUARKUS_DATASOURCE_PASSWORD')?.valueFrom?.secretKeyRef
  if (job.kind !== 'Job' || job.metadata?.namespace !== 'hero-association' ||
      job.metadata?.generateName !== 'world-db-bootstrap-' ||
      pod?.containers?.length !== 1 || pod.automountServiceAccountToken !== false ||
      bootstrap?.imagePullPolicy !== 'Never' ||
      environment.get('QUARKUS_DATASOURCE_JDBC_URL')?.value !==
        'jdbc:postgresql://postgres-world:5432/hero_association_world' ||
      environment.get('QUARKUS_DATASOURCE_USERNAME')?.value !== 'hero_association_world' ||
      passwordSecret?.name !== 'hero-association-world-credentials' ||
      passwordSecret?.key !== 'WORLD_DATABASE_PASSWORD' ||
      environment.get('QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY')?.value !== 'drop-and-create' ||
      environment.get('QUARKUS_HIBERNATE_ORM_SQL_LOAD_SCRIPT')?.value !== 'import.sql' ||
      environment.get('HERO_ASSOCIATION_WORLD_BOOTSTRAP_MODE')?.value !== 'true' ||
      !/^hero-association-world:[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/.test(imageRef)) {
    throw new Error('World bootstrap Job or archived World image is not the expected isolated k3d target')
  }
  bootstrap.image = imageRef
  return job
}

export function assertWorldDatabaseIdentity(identity) {
  if (identity !== 'hero_association_world|hero_association_world') {
    throw new Error('Refusing to reset an unexpected World database: ' + identity)
  }
}
