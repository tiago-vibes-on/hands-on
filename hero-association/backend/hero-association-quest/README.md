# Quest service

This service builds independently from the backend root:

```bash
./mvnw -pl hero-association-quest -am package
```

For hot reload against the daily lab, run `../deploy/k3d/hybrid.sh run quest`
from `backend/`. For standalone module commands, install shared libraries from
the backend root, then enter this service directory:

```bash
./mvnw -pl hero-association-lib/combat-engine,hero-association-lib/game-contracts -am install
cd hero-association-quest
./mvnw quarkus:dev
```

Supply this service's database and Keycloak URL; Quest also needs Core and Assets
URLs and its service keys. Compose supplies these dependencies and variables.

Dev HTTP uses `17087`; production defaults to `8087`. Dev PostgreSQL
uses its own `hero_association_quest` database/role and recreates deterministic
seed data. Configure `QUARKUS_DATASOURCE_JDBC_URL`, username/password and
`QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY=validate` to use an existing
schema. The isolated component runner supplies disposable PostgreSQL; hybrid
mode preserves daily data and obtains credentials from the local k3d Secrets.

See [the backend workflows](../README.md) and
[World/Quest contracts](../../WORLD_QUEST_ARCHITECTURE.md) for API, credentials,
version pinning, admission, payout and recovery rules. Public APIs validate
Keycloak JWTs with the `hero-association-quest` audience. Internal routes require
separate service credentials and mesh identities.
