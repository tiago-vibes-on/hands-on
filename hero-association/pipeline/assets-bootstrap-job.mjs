export function prepareAssetsBootstrapJob(template, imageRef) {
  const job = structuredClone(template)
  const pod = job.spec?.template?.spec
  const bootstrap = pod?.containers?.find((container) => container.name === 'bootstrap')
  const environment = new Map(bootstrap?.env?.map((entry) => [entry.name, entry]) ?? [])
  const passwordSecret = environment.get('QUARKUS_DATASOURCE_PASSWORD')?.valueFrom?.secretKeyRef
  if (job.kind !== 'Job' || job.metadata?.namespace !== 'hero-association' ||
      job.metadata?.generateName !== 'assets-db-bootstrap-' ||
      pod?.containers?.length !== 1 || pod.automountServiceAccountToken !== false ||
      bootstrap?.imagePullPolicy !== 'Never' ||
      environment.get('QUARKUS_DATASOURCE_JDBC_URL')?.value !==
        'jdbc:postgresql://postgres-assets:5432/hero_association_assets' ||
      environment.get('QUARKUS_DATASOURCE_USERNAME')?.value !== 'hero_association_assets' ||
      passwordSecret?.name !== 'hero-association-assets-credentials' ||
      passwordSecret?.key !== 'ASSETS_DATABASE_PASSWORD' ||
      environment.get('QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY')?.value !== 'drop-and-create' ||
      environment.get('QUARKUS_HIBERNATE_ORM_SQL_LOAD_SCRIPT')?.value !== 'import.sql' ||
      environment.get('HERO_ASSOCIATION_ASSETS_BOOTSTRAP_MODE')?.value !== 'true' ||
      !/^hero-association-assets:[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/.test(imageRef)) {
    throw new Error('Assets bootstrap Job or archived Assets image is not the expected isolated k3d target')
  }
  bootstrap.image = imageRef
  return job
}

export function assertAssetsDatabaseIdentity(identity) {
  if (identity !== 'hero_association_assets|hero_association_assets') {
    throw new Error('Refusing to reset an unexpected Assets database: ' + identity)
  }
}
