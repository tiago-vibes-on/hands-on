import assert from 'node:assert/strict'
import { prepareSchemaReset } from './schema-reset.mjs'

for (const database of ['hero_association', 'hero_association_assets', 'hero_association_market']) {
  assert.ok(prepareSchemaReset(database, `${database}|${database}`).includes(`AUTHORIZATION "${database}"`))
  assert.throws(() => prepareSchemaReset(database, `${database}|postgres`))
  assert.throws(() => prepareSchemaReset(database, 'other_database|other_database'))
}
assert.throws(() => prepareSchemaReset('postgres', 'postgres|postgres'))
assert.throws(() => prepareSchemaReset('hero_association; DROP DATABASE postgres', 'hero_association|hero_association'))
