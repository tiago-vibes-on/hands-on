const databases = new Set(['hero_association', 'hero_association_assets', 'hero_association_market'])

/** Only the explicit, verified pre-Flyway coupled reset may discard these schemas. */
export function prepareSchemaReset(database, identity) {
  if (!databases.has(database) || identity !== `${database}|${database}`) {
    throw new Error('Refusing to reset an unexpected game database or role')
  }
  return `DROP SCHEMA public CASCADE; CREATE SCHEMA public AUTHORIZATION "${database}";`
}
