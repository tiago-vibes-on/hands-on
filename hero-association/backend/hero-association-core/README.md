# Hero Association Game Core

Core owns Accounts, Managers, agency membership, Heroes, Parties, recruitment,
feed posts and permanent Hero progression in its own PostgreSQL database.
Assets owns wallets, inventory, catalogs and equipment; Market owns orders;
World owns versioned Creature and Map definitions; Quest owns optional objectives.
BFF is the browser API boundary. See [the backend guide](../README.md) and
[World and Quest contracts](../../WORLD_QUEST_ARCHITECTURE.md).

## Build and test

Use Java 25 and Docker. From `backend/`, build Core and its shared libraries:

```bash
./mvnw -pl hero-association-core -am test
./mvnw -pl hero-association-core -am package
```

Core tests use temporary PostgreSQL through Quarkus Dev Services and a
RabbitMQ container for the settlement consumer. Core has no Redis dependency.
The archived-image pipeline instead supplies disposable k3d dependencies.

For direct commands inside this module, first install both libraries from
`backend/`:

```bash
./mvnw -pl hero-association-lib/combat-engine,hero-association-lib/game-contracts -am install
```

Then `./mvnw test`, `./mvnw package` and `./mvnw quarkus:dev` work from this
module. The JVM fast-jar is under `target/quarkus-app/`.

## Local development

PostgreSQL, Keycloak, RabbitMQ and Envoy Gateway stay in k3d. From
`hero-association/deploy/k3d`:

```bash
./hybrid.sh run core
```

The command forwards Core's database and private Assets, World, Quest and
broker dependencies, stops Core Pods, and runs Quarkus hot reload on `17081`.
Browser requests still enter through `https://heroassociation.test` and BFF.
Ctrl-C restores the previous replicas, Service route and HPA. Hybrid mode
validates the existing schema and preserves data. See
[local development](../../LOCAL_DEVELOPMENT.md) for interruption recovery.

## Equipment and Expedition settlement

Rune equip and unequip require a UUIDv7 operation key. Core fences the Hero
while a durable command awaits Assets. A worker retries the identical command;
`GET /api/v1/asset-operations/{operationKey}` reports its status to its Manager.
Unknown delivery returns `202`; a verified Assets receipt completes the change.

Expedition admission reserves eligible personal Heroes and persists a complete
World plan before pinning Assets loadouts and an optional Quest assignment.
Exact retries reuse the same versions. On return, Core validates the complete
frozen aggregate, confirms Quest progress and its Assets payout, credits carried
Assets, then applies permanent Hero progression through a durable cursor.
Duplicate delivery cannot repeat rewards or XP. Core acknowledges only after
all owners confirm; reservations block a new run during recovery. See
[Assets recovery](../../ASSETS_RECOVERY.md) and
[Expedition settlement](../../EXPEDITION_SETTLEMENT.md).

Core's Quest-start and combat-sync APIs, Quest combat tables and scheduler,
Creature table and Creature cache are retired. Quest acceptance selects no Party
and starts no combat. Its public API belongs to the Quest service.

## Database and seed data

Development and test profiles recreate the disposable schema and load
`src/main/resources/import.sql`. Hybrid and normal packaged k3d processes
validate it. One-shot bootstrap Jobs create schemas and deterministic data;
ordinary Pod restarts preserve data. For an incompatible pre-Flyway change,
restore full k3d mode and promote a verified eight-service archive with
`--reset-game-db`. This recreates the matching Core, Assets, Market, World and
Quest databases after refusing active runs and unresolved work.

Core seeds three agencies, Manager memberships, personal and agency Heroes,
prepared Parties, global recruits and feed posts. Assets separately seeds
wallets, items, runes, equipment and reservations for Market's orders. World
seeds Troll Field and Broken Pass Cavern; Quest seeds four definitions with no
accepted assignments. User 2 has 100,000 personal gold for disposable tests.
See [the seed map](../../TEST_DATA.md).

Core's agency recovery worker uses PostgreSQL advisory locks across replicas.
It restores health, mana and stamina from elapsed time; Training uses class
recovery and Resting uses the agency Rest Level multiplier. Permanent combat
changes arrive only through Expedition settlement. The earlier SQL Quest
concurrency and mixed-combat labs require redesign for Expedition and are not
supported combat checks for this extraction.

## Docker Compose and native builds

From `backend/`, `docker compose up --build` runs the JVM services with private
Core and browser-facing BFF on `http://localhost:17080`. Compose resets its
disposable game databases on startup. Use k3d for the integrated HTTPS browser.

Native Core is optional and needs GraalVM or Mandrel. After installing the
shared libraries, run from this module:

```bash
./mvnw package -Dnative
# Or build with a container toolchain:
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

The executable is `target/hero-association-core-0.2.0-SNAPSHOT-runner`.
From `backend/`, `docker compose -f compose.native.yaml up --build` uses
`Dockerfile.native`'s `native-runtime` target and that prebuilt executable.
The `native-multistage` target can compile it from the backend build context:

```bash
docker build --file hero-association-core/Dockerfile.native --target native-multistage --tag hero-association-core:native .
```

The multistage image skips tests; run the relevant JVM suite separately.

## API boundaries

Core listens privately on `8081`. BFF forwards the authenticated player token
for Account, agency, recruitment, Party, activity, borrowing-fee configuration,
rune and feed commands. Agency membership, leadership and personal ownership
are enforced server-side. Away Heroes reject activity and equipment changes;
reserved Parties reject membership changes. Agency borrowing-fee configuration
is retained for future Expedition borrowing, without charging current runs.

Agency state includes upgrade levels, agency and personal Heroes, Party member
IDs, Assets inventory/equipment projections and feed posts. It contains no
Quest definitions or combat snapshots. Public World, Quest, Market and
Expedition routes go to their respective owners through BFF.

Private service-key routes expose player authority and Expedition admission,
release and settlement contracts. Core's retired Assets reservation routes
return `404`. Service credentials never reach the browser.

Core exports OTLP traces, HTTP/JVM metrics and structured logs in k3d;
telemetry is disabled by default in host and Compose workflows.
