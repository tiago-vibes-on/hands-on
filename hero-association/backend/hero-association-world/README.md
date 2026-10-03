# World service

This service builds independently from the backend root:

```bash
./mvnw -pl hero-association-world -am package
```

For hot reload against the daily lab, run `../deploy/k3d/hybrid.sh run world`
from `backend/`. For standalone module commands, install shared libraries from
the backend root, then enter this service directory:

```bash
./mvnw -pl hero-association-lib/combat-engine,hero-association-lib/game-contracts -am install
cd hero-association-world
./mvnw quarkus:dev
```

Supply this service's database and Keycloak URL; Quest also needs Core and Assets
URLs and its service keys. Compose supplies these dependencies and variables.

Dev HTTP uses `17086`; production defaults to `8086`. Dev PostgreSQL
uses its own `hero_association_world` database/role and recreates deterministic
seed data. Configure `QUARKUS_DATASOURCE_JDBC_URL`, username/password and
`QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY=validate` to use an existing
schema. The isolated component runner supplies disposable PostgreSQL; hybrid
mode preserves daily data and obtains credentials from the local k3d Secrets.

Creature `goldDrop` and each `drops` entry carry `minimumQuantity`,
`maximumQuantity` and `chance`; ranges are uniform and inclusive. Expedition
rolls every entry independently per defeated Creature using the admitted
version. The seeds contain the gold, rune and material rates in
[the game specification](../../SPEC.md). Deploy the new drop contract and matching
seed data together, after returning active runs, as described in the backend workflows.

See [the backend workflows](../README.md) and
[World/Quest contracts](../../WORLD_QUEST_ARCHITECTURE.md) for API, credentials,
version pinning, admission, payout and recovery rules. Public APIs validate
Keycloak JWTs with the `hero-association-world` audience. Internal routes require
separate service credentials and mesh identities.
