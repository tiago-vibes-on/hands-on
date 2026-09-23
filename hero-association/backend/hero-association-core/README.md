# Hero Association Game Core

Game Core is the private Quarkus service that owns Hero Association game rules
and PostgreSQL state. The public BFF forwards the browser's existing API calls
to this service without changing the API contract. See
[`../README.md`](../README.md) for the complete service topology.

## Prerequisites

- Java 25 LTS
- Docker with Docker Compose

## Test

From this directory, run:

```bash
./mvnw test
```

The test suite starts PostgreSQL automatically through Quarkus Dev Services.

## Local development (default)

Run PostgreSQL and Keycloak in Docker Compose, then run Quarkus directly on the
host. This keeps the database lifecycle separate from the microservice and
enables Quarkus hot reload without rebuilding a container.

From the parent `backend/` directory, first create the ignored Keycloak
environment file required by Compose:

```bash
cd ..
cp .env.example .env
```

Then start the local infrastructure:

```bash
# Terminal 1, from backend/
docker compose -f compose.infra.yaml up --detach

# Terminal 2: run Game Core on the host
cd hero-association-core
./mvnw quarkus:dev
```

The development profile connects to the Compose database at `localhost:5432`
with the seeded `hero_association` credentials and validates bearer tokens
issued by Keycloak at `http://localhost:8180`. Quarkus Dev Services is disabled
for this profile. Game Core listens on `http://localhost:8081`. Use the BFF at
`http://localhost:8080` for browser requests; it forwards the server-held
access token. The test profile continues to start its own temporary PostgreSQL
container through Dev Services.

### Development database reset

Until Flyway is introduced, every application startup drops and recreates the
database schema, then loads deterministic game data from
`src/main/resources/import.sql`. The seed contains Dawnwatch Agency, its
leader, six heroes, Broken Pass Party, an in-progress troll quest and its
initial combat snapshot, seven rune definitions, Magic Crystals, Iron Ingots,
agency rune inventory, the party's equipped runes, two feed posts, Ironridge
Exchange, and its open market orders. Do not use this configuration with data
that must be retained.

### Stop the local database

Stop the standalone database while preserving its data:

```bash
cd ..
docker compose stop postgres-core
```

The current development configuration drops and recreates the schema on every
Quarkus startup, so the Compose volume does not preserve game data yet.

## Package

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
docker build --file Dockerfile.native --target native-multistage --tag hero-association-core:native .
```

The multistage target skips tests because Docker builds cannot safely run the
Testcontainers PostgreSQL workflow. Run `./mvnw test` separately before using
that convenience target.

Stop it with:

```bash
cd ..
docker compose -f compose.native.yaml down
```

After signing in through the frontend, the BFF forwards the seeded API at
`http://localhost:8080/api/v1/agencies/019c4c00-0001-7000-8000-000000000001/state`.

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

The API provisions the authenticated Account and supports agency-state reads
and persisted rune loadouts. Agency state and all agency-specific commands
require an `AgencyMember` record for the authenticated Manager. Both roles can
operate gameplay commands; only `LEADER` can create or cancel market orders:

- `GET /api/v1/account`
- `POST /api/v1/account/manager`
- `GET /api/v1/agencies/{agencyId}/state`
- `PUT /api/v1/agencies/{agencyId}/heroes/{heroId}/rune-slots/{slotIndex}`
- `DELETE /api/v1/agencies/{agencyId}/heroes/{heroId}/rune-slots/{slotIndex}`
- `PUT /api/v1/agencies/{agencyId}/heroes/{heroId}/activity`
- `POST /api/v1/agencies/{agencyId}/parties`
- `PUT /api/v1/agencies/{agencyId}/parties/{partyId}/heroes/{heroId}`
- `DELETE /api/v1/agencies/{agencyId}/parties/{partyId}/heroes/{heroId}`
- `PUT /api/v1/agencies/{agencyId}/quests/{questId}/start`
- `POST /api/v1/agencies/{agencyId}/quests/{questId}/combat/sync`
- `POST /api/v1/agencies/{agencyId}/feed-posts`
- `GET /api/v1/market/orders`
- `POST /api/v1/agencies/{agencyId}/market-orders`
- `DELETE /api/v1/agencies/{agencyId}/market-orders/{orderId}`

It returns the agency and leader, Agency, Training, Rest, Size, Reputation, and
Intelligence upgrade levels, all heroes and their class recovery values,
parties with quests and member IDs, agency item and rune inventory, and each
hero's five rune slots. The initial item inventory contains stackable
materials; item equipment and quest loot are pending. Rest represents the
agency's recovery facilities; there is no Medical
Level, and its concrete upgrade effect remains to be defined. Quest definitions
include their description, creature objective,
party-size range, duration estimate, gold reward, and status. Equipping or
replacing a rune decrements its
agency inventory quantity and returns any replaced rune to inventory in the
same transaction. An unknown agency or hero returns `404 Not Found`; an
unavailable rune returns `409 Conflict`. Agency heroes can switch between
`TRAINING` and `RESTING`; a hero on a quest cannot change activity and returns
`409 Conflict`. A manager can create a uniquely named prepared party and add
or remove available agency heroes. Prepared members keep their activity until
a quest starts. An in-progress quest party cannot have its membership changed,
and a hero already on a quest cannot move to another party; both return `409
Conflict`. Starting an `AVAILABLE` quest with an eligible prepared party moves
all of its members to `ON_QUEST`; a party outside the quest's required size
returns `400 Bad Request`. The resulting quest state includes its persisted
start and expected-completion timestamps. The backend includes a deterministic,
unit-tested combat rules engine for independent attack timers, hero recovery,
mage spells, critical hits, deaths, and battle completion. The seeded Troll
quest has an API-visible, persisted combat snapshot. Its combat-sync command
advances that snapshot by elapsed time and atomically stores the result plus
the latest 100 server-generated combat events; ordinary state reads remain
read-only. A five-second background worker advances every active snapshot while
the API is running. New quests do not yet create a snapshot, and quest
completion, rewards, stamina, and death resolution remain unavailable. Each
combat synchronization persists the current health and mana of heroes in that
encounter. A separate five-second background worker restores agency hero health
and mana from elapsed time: Training uses the base class rate and Resting uses
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
listed in its Account memberships and loads its state through the BFF. It then
persists rune drawer, agency-activity, prepared-party, feed, and market-order
changes through that public boundary. Its expanded seeded combat view renders
the Core snapshot and calls the combat-sync endpoint every two seconds; Phaser
does not simulate combat and only replays newly received server events. The
frontend also loads the quest board and starts available quests through the
BFF. Future endpoints will cover recruiting heroes and quest resolution.
