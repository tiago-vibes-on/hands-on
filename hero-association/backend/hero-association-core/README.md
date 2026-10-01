# Hero Association Game Core

Game Core is the private Quarkus service that owns Hero Association game rules
and PostgreSQL state. The public BFF forwards the browser's existing API calls
to this service without changing the API contract. See
[`../README.md`](../README.md) for the complete service topology.

## Prerequisites

- Java 25 LTS
- Docker with Docker Compose

## Test

For a clean checkout, run `./mvnw -pl hero-association-core -am test` from
`backend/`; this builds the shared combat engine too. To run the direct
module commands below, first run
`./mvnw -pl hero-association-lib/combat-engine -am install` from `backend/`. Then, from
this directory, run:

```bash
./mvnw test
```

The test suite starts temporary PostgreSQL and Redis containers through Quarkus
Dev Services. Redis caches Creature combat profiles; PostgreSQL remains authoritative.
The inbox consumer test also starts a temporary RabbitMQ container. The
Redis-outage profile test runs in a separate Maven test fork so a Quarkus
profile restart cannot reuse a stopped PostgreSQL Dev Services port.


The k3d Core exports OTLP traces, HTTP/JVM metrics, and structured logs
to the isolated collector. Normal host-run development and Docker Compose
keep telemetry disabled unless explicitly enabled; see
[`../README.md`](../README.md#isolated-k3d-jvm-deployment).

## Local development (default)

Shared PostgreSQL, Keycloak, Redis, RabbitMQ, and Envoy Gateway stay in k3d.
From `hero-association/deploy/k3d`, run:

```bash
k3d cluster start hero-association
./hybrid.sh run core
```

The command stops only the Core k3d workload, forwards its private
dependencies, and runs Core in Quarkus dev mode with hot reload on port
`17081`. Browser requests still enter through
`https://heroassociation.test` and the BFF. Press Ctrl-C to restore the
original Core replicas, Service route, and HPA. Core validates the existing
database schema and does not reseed it during a reload. See the
[local development guide](../../LOCAL_DEVELOPMENT.md) for ports, secrets,
mixed-service mode, interruption recovery, and intentional Core reset.
Direct `./mvnw test` remains independently runnable and may start local
Dev Services; backend candidate builds use disposable k3d dependencies.

### Pre-cutover Expedition settlement (off by default)

Core can reserve a personal-Hero Party under one UUIDv7 Expedition ID and
stores the exact baseline in `expedition_reservation`. No browser or private
entry route calls this method yet. A dedicated RabbitMQ consumer can apply one
frozen Expedition aggregate to Hero and Manager Assets in the same transaction
as the reservation receipt. Duplicate bytes are harmless; a changed baseline
or conflicting payload is rejected. Core publishes a separate owner-applied
acknowledgment only after SQL commit. This consumer remains disabled until
authenticated admission is connected. See
[Expedition settlement](../../EXPEDITION_SETTLEMENT.md) for the handoff and
local broker settings.

### Development database reset

Until Flyway is introduced, the development and test profiles drop and
recreate the database schema on startup, then load deterministic game data from
`src/main/resources/import.sql`. The seed contains Dawnwatch Agency, its
leader and six heroes, the three globally available recruitment NPCs, Broken
Pass Party, an in-progress troll quest and its initial combat snapshot, seven
rune definitions, Magic Crystals, Iron Ingots, agency rune inventory, the
party's equipped runes, two feed posts, Ironridge Exchange, and its open market
orders. It also seeds manager1 through manager10 across Dawnwatch, Ironridge,
and Silverkeep Guild. Every seeded Manager has three personal starter heroes,
most have zero personal gold, and User 2 has 100,000 personal gold for
local testing. Manager 3 and Manager 4 have small personal item stacks for
market tests. Every seeded Manager owns one Party; User 1 has Broken Pass
Party, and the others have a Main Party with their three personal heroes. Agency
assets remain separate. New Manager onboarding provisions the same three
heroes, a Main Party, zero gold, and no items before agency creation.
`GET /api/v1/account` exposes these personal assets under `manager`.
See the [test-data map](../../TEST_DATA.md). Do not use this configuration
with data that must be retained.

A party belongs to its Manager. The onboarding Main Party is initially
unattached and joins the Manager's agency when they create one.
That Manager can assign or remove their available personal heroes or available
agency-owned heroes and start a quest with their prepared party; other
Managers cannot change or launch it.
`GET /api/v1/agencies/{agencyId}/state` keeps agency-owned heroes in
`heroes` and returns the caller's personal heroes (plus personal heroes
participating in agency parties) in `personalHeroes`. The seeded Broken
Pass party belongs to User 1 and retains its older agency-hero members.
Agency-hero assignment is free and does not transfer ownership. The agency
leader can set each agency hero's per-quest fee, defaulting to 0 gold.

The progression schema now stores cumulative XP, fractional Melee, Distance,
Magic, and Shield points, and up to 48 hours of stamina in milliseconds.
Hero and skill levels derive from those totals; class-specific maximum health
and mana derive from hero level. Restarting `quarkus:dev` recreates and reseeds
the local database with this schema. The existing Hero API still returns a
percentage stamina value until the planned frontend/API update.

Combat synchronization now drains stamina for each living hero by active
battle time and grants Melee or Distance points for Warrior/Archer attacks
and Magic points from mana actually spent. It processes every new combat
event before retaining only the latest 100 for the UI. Each creature
defeat grants its 100-XP provisional base separately to every living party
hero, adjusted by individual stamina at the kill time; repeated syncs do not
award it twice. Shield blocking and agency stamina recovery remain planned.

The packaged JVM and native Compose stacks opt into the same reset behavior
explicitly. A packaged Core started without that override instead validates
its schema and does not load seed data. In k3d, `deploy-backend.sh` runs a
one-shot bootstrap Job only when the schema is absent; `--reset-core-db`
requests a destructive lab reset. Ordinary Core Pod restarts preserve data.
An existing k3d lab database using the earlier Hero schema, including one
without Manager-owned assets, needs an explicit `--reset-core-db` redeploy;
normal Pod restarts cannot migrate it. Do not reset data you need to retain.
Scheduled progression and recovery use separate PostgreSQL transaction locks,
so concurrent Pods skip competing ticks. An isolated k3d concurrency test
checks recovery, combat, and quest resolution while scaling from two to eight
Pods under concurrent database reads. The k3d Core HPA runs two to eight
replicas; normal Core Pods validate the shared database schema.

### Reset disposable Core data intentionally

Hybrid dev mode does not drop or recreate the shared k3d database. To reset
Core game data after an incompatible schema change, first restore full k3d
mode and use the explicit reset/reseed workflow in the
[local development guide](../../LOCAL_DEVELOPMENT.md). Do not reset data you
need to preserve.

## Package

The combat rules engine is shared with the isolated Combat sandbox through
`../hero-association-lib/combat-engine`. For a clean build, run from
`backend/`: `./mvnw -pl hero-association-core -am package`. To use the
standalone `./mvnw` commands below (including dev mode), first run
`./mvnw -pl hero-association-lib/combat-engine -am install` from `backend/`.

The default package is a JVM fast-jar:

```bash
./mvnw package
```

### Native executable (optional)

Build a native executable with GraalVM or Mandrel and its `native-image` tool
installed:

```bash
./mvnw package -Dnative
```

The native executable is written to
`target/hero-association-core-0.2.0-SNAPSHOT-runner`.
Native builds are opt-in because they take longer and require a native-image
toolchain. If no local GraalVM or Mandrel installation is available, Docker can
perform the native compilation instead:

```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

## Run with Docker Compose

The complete stack is started from the parent `backend/` directory so the BFF
is the public API boundary:

```bash
cd ..
docker compose up --build
```

Docker Compose builds and runs the JVM Core and BFF packages by default. Core
is only reachable on the private Compose network; the BFF is available on port
`8080`. The native build commands above do not change that workflow.

### Run the native executable with Docker Compose

First create a Linux native executable. The command runs the test suite before
building the executable in a container:

```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

Then build and run the native Core behind the JVM BFF:

```bash
cd ..
docker compose -f compose.native.yaml up --build
```

`compose.native.yaml` selects the Core's `native-runtime` target from
`hero-association-core/Dockerfile.native`, which copies the prebuilt and tested
executable. The same Dockerfile also has a `native-multistage` target that
compiles natively during the Docker build:

```bash
docker build --file hero-association-core/Dockerfile.native --target native-multistage --tag hero-association-core:native .
```

Run that Docker command from `backend/`, which supplies the shared library
and Core as one build context.

The multistage target skips tests because Docker builds cannot safely run the
Testcontainers PostgreSQL workflow. Run `./mvnw test` separately before using
that convenience target.

Stop it with:

```bash
cd ..
docker compose -f compose.native.yaml down
```

After signing in through the frontend, the BFF forwards the seeded API at
`http://localhost:17080/api/v1/agencies/019c4c00-0001-7000-8000-000000000001/state`.

To stop the containers:

```bash
cd ..
docker compose down
```

To also remove the local PostgreSQL volume:

```bash
cd ..
docker compose down --volumes
```

## Internal API

Game Core exposes this API on port `8081` for the BFF. Do not configure the
frontend to call Core directly; use the BFF on port `8080` instead.

The API provisions the authenticated Account and supports agency-state reads,
recruitment, and persisted rune loadouts. A Manager without an `AgencyMember`
record can create one empty Level 1 agency as its `LEADER`. `GET /api/v1/recruits`
and `POST /api/v1/recruits/{recruitId}/claim` require an onboarded Manager
but not agency membership. A claim assigns the NPC to the Manager's personal
roster. An agency leader can instead explicitly claim a recruit for the agency
through `POST /api/v1/agencies/{agencyId}/recruits/{recruitId}/claim`. A recruit
can be claimed only once across both routes. Agency-specific commands require
membership. Only `LEADER` can claim for an agency or create or cancel market
orders.

- `GET /api/v1/account`
- `POST /api/v1/account/manager`
- `POST /api/v1/agencies`
- `GET /api/v1/recruits`
- `POST /api/v1/recruits/{recruitId}/claim`
- `POST /api/v1/agencies/{agencyId}/recruits/{recruitId}/claim`
- `GET /api/v1/agencies/{agencyId}/heroes/{heroId}`
- `GET /api/v1/agencies/{agencyId}/state`
- `PUT /api/v1/agencies/{agencyId}/heroes/{heroId}/rune-slots/{slotIndex}`
- `DELETE /api/v1/agencies/{agencyId}/heroes/{heroId}/rune-slots/{slotIndex}`
- `PUT /api/v1/agencies/{agencyId}/heroes/{heroId}/activity`
- `PUT /api/v1/agencies/{agencyId}/heroes/{heroId}/borrowing-fee` (leader-only JSON body: `{ "feeGold": 0 }`)
- `POST /api/v1/agencies/{agencyId}/parties`
- `PUT /api/v1/agencies/{agencyId}/parties/{partyId}/heroes/{heroId}`
- `DELETE /api/v1/agencies/{agencyId}/parties/{partyId}/heroes/{heroId}`
- `PUT /api/v1/agencies/{agencyId}/quests/{questId}/start`
- `POST /api/v1/agencies/{agencyId}/quests/{questId}/combat/sync`
- `POST /api/v1/agencies/{agencyId}/feed-posts`
- `GET /api/v1/market/orders`
- `POST /api/v1/market/orders` (JSON body includes `ownerType`, `side`, `itemId`, `quantity`, and `priceGoldPerItem`; include `agencyId` only when `ownerType` is `AGENCY`)
- `DELETE /api/v1/market/orders/{orderId}`

Market mutations return the order's owner type, ID, name, and status, not
account or agency state. `MANAGER` uses the authenticated Manager's personal
wallet and inventory; `AGENCY` requires leadership and uses agency assets.
Refresh both `GET /api/v1/account` and
`GET /api/v1/agencies/{agencyId}/state` after placing or cancelling an order.
Core still executes matching and resource transfers in one PostgreSQL
transaction until Market is extracted as its own service.


The initial global board contains free Level 1 NPCs. A candidate is globally
unique, so a successful claim removes it from every Manager's board. The claim
returns the personal hero, who starts in `TRAINING` with full class health,
mana, and 100% stamina. Agency leaders can explicitly claim a recruit for the agency instead.

Agency state returns the agency and leader, Agency, Training, Rest, Size,
Reputation, and Intelligence upgrade levels, agency and personal heroes and
class recovery values,
parties with quests and member IDs, agency item and rune inventory, and each
hero's five rune slots. The initial item inventory contains stackable
materials; item equipment and quest loot are pending. Rest represents the
agency's recovery facilities; there is no Medical
Level, and its concrete upgrade effect remains to be defined. Quest definitions
include their description, creature objective,
party-size range, duration estimate, gold reward, and status. Equipping or
replacing a rune decrements its agency inventory quantity and returns any
replaced rune to inventory in the same transaction. An unknown agency or hero
returns `404 Not Found`; an unavailable rune or an attempt to change a quest
hero's loadout returns `409 Conflict`. Agency heroes can switch between
`TRAINING` and `RESTING`; a hero on a quest cannot change activity and returns
`409 Conflict`. A manager can create a uniquely named prepared party and add
or remove available agency heroes. Prepared members keep their activity until
a quest starts. An in-progress quest party cannot have its membership changed,
and a hero already on a quest cannot move to another party; both return `409
Conflict`. Starting an `AVAILABLE` quest requires JSON body
`{ "partyId": "...", "expectedBorrowingFeeGold": 0 }`. Core sums the
agency-owned party heroes' fees and, only when the quote matches and the
Manager has enough personal gold, transfers that total to the agency and
moves all members to `ON_QUEST` in the same transaction. The leader pays
when borrowing too. A stale quote or insufficient funds returns `409 Conflict`
without charging. A party outside the quest's required size returns `400 Bad
Request`. The resulting quest state includes its persisted start and
expected-completion timestamps. The backend includes a deterministic,
unit-tested combat rules engine for independent attack timers, hero recovery,
mage spells, critical hits, deaths, and battle completion. Every quest start
creates an API-visible combat snapshot from its party's current resources,
class combat values, and equipped Critical Chance and Critical Damage Rune
effects, plus one creature per required objective. Until
per-creature difficulty is designed, each new creature uses a shared
provisional profile: 120 health, 10 damage, a 1.6-second attack interval, 100
mana, no recovery, and no critical chance. The combat-sync command advances a
snapshot by elapsed time and atomically stores the result plus its latest 100
server-generated combat events; ordinary state reads remain read-only. A
five-second background worker advances every active snapshot while the API is
running. When combat reaches `HERO_VICTORY`, its quest becomes `COMPLETED`;
when it reaches `CREATURE_VICTORY`, it becomes `FAILED`. Both outcomes set
`finishedAt`, release the party, and return its heroes to `TRAINING`.
Combat synchronization persists hero health and mana, drains active-battle
stamina, grants eligible Melee, Distance, and Magic skill points, and awards
creature XP to living party heroes. Economic rewards and the non-permanent
defeat penalty remain planned; permanent death and death fees are no longer
part of the current design. A separate five-second background worker restores
agency hero health and mana from elapsed time: Training uses the base class rate and Resting uses
twice that rate. Stamina recovery is not implemented yet.
Agency state also includes newest-first feed posts. A 500-character text post
can be created as the agency, its current leader, or any hero belonging to the
agency. A post may reference one item stack and a positive quantity currently
held by the agency; this does not consume or reserve that inventory. Membership
authorization is enforced; feed visibility beyond an agency is not implemented
yet.
The market exposes the global open order book and lets an agency create or
cancel its own orders. A buy order reserves its maximum gold value, while a
sell order reserves its items. Compatible orders match by price and creation
time at the resting order's price. The buyer receives the item, the seller
receives 90% of the trade value, and the 10% fee is removed from the game
economy. Cancelling an open order returns the remaining reservation. Order
history is not implemented yet.

After a browser signs in through the BFF, the React frontend opens an agency
listed in its Account memberships and loads its state through the BFF. It
persists recruitment, rune drawer, agency-activity, prepared-party, feed, and
market-order changes through that public boundary. Its expanded active combat
view renders the Core snapshot and calls the combat-sync endpoint every two
seconds; Phaser does not simulate combat and only replays newly received server
events. Starting an available quest through the BFF creates its combat snapshot
immediately. Rewards and permanent death remain future work.
