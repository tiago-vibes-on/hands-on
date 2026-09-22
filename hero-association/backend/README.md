# Hero Association Backend

The Hero Association backend is a Quarkus API backed by PostgreSQL. Its first
game-state endpoint exposes an agency, its leader, heroes, party and quest,
item and rune inventory, and equipped rune slots.

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

Run PostgreSQL in Docker Compose, then run Quarkus directly on the host. This
keeps the database lifecycle separate from the microservice and enables Quarkus
hot reload without rebuilding a container.

```bash
# Terminal 1: start only PostgreSQL
docker compose up --detach postgres

# Terminal 2: run the API on the host
./mvnw quarkus:dev
```

The development profile connects to the Compose database at `localhost:5432`
with the seeded `hero_association` credentials. Quarkus Dev Services is disabled
for this profile. The test profile continues to start its own temporary
PostgreSQL container through Dev Services.

### Development database reset

Until Flyway is introduced, every application startup drops and recreates the
database schema, then loads deterministic game data from
`src/main/resources/import.sql`. The seed contains Dawnwatch Agency, its
leader, six heroes, Broken Pass Party, an in-progress troll quest and its
initial combat snapshot, seven rune definitions, Magic Crystals, Iron Ingots,
agency rune inventory, the party's equipped runes, and two feed posts. Do not
use this configuration with data that must be retained.

### Stop the local database

Stop the standalone database while preserving its data:

```bash
docker compose stop postgres
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

The native executable is written to `target/hero-association-0.2.0-SNAPSHOT-runner`.
Native builds are opt-in because they take longer and require a native-image
toolchain. If no local GraalVM or Mandrel installation is available, Docker can
perform the native compilation instead:

```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

## Run with Docker Compose

```bash
docker compose up --build
```

Docker Compose builds and runs the JVM package by default. The native build
commands above do not change that workflow.

### Run the native executable with Docker Compose

First create a Linux native executable. The command runs the test suite before
building the executable in a container:

```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

Then build and run the native runtime image with its dedicated Compose file:

```bash
docker compose -f compose.native.yaml up --build
```

`compose.native.yaml` selects the `native-runtime` target from
`Dockerfile.native`, which copies the prebuilt and tested executable. The same
Dockerfile also has a `native-multistage` target that compiles natively during
the Docker build:

```bash
docker build --file Dockerfile.native --target native-multistage --tag hero-association:native .
```

The multistage target skips tests because Docker builds cannot safely run the
Testcontainers PostgreSQL workflow. Run `./mvnw test` separately before using
that convenience target.

Stop it with:

```bash
docker compose -f compose.native.yaml down
```

The seeded API is available at
`http://localhost:8080/api/v1/agencies/019c4c00-0001-7000-8000-000000000001/state`.

To stop the containers:

```bash
docker compose down
```

To also remove the local PostgreSQL volume:

```bash
docker compose down --volumes
```

## API

The first API supports agency-state reads and persisted rune loadouts:

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

It returns the agency and leader, Agency, Training, Rest, Size, Reputation, and
Intelligence upgrade levels, all heroes and their class recovery values,
parties with quests and member IDs, agency item and rune inventory, and each
hero's five rune slots. The initial item inventory contains read-only stackable
materials; item equipment, loot, and market commands are pending. Rest
represents the agency's recovery facilities; there is no Medical
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
held by the agency; this does not consume or reserve that inventory. Authentication
and visibility beyond an agency are not implemented yet.

Read the seeded state:

```bash
curl http://localhost:8080/api/v1/agencies/019c4c00-0001-7000-8000-000000000001/state
```

The React frontend loads this endpoint when it starts and persists rune drawer,
agency-activity, and prepared-party changes through the API. Its expanded
seeded combat view renders the backend snapshot and calls the combat-sync
endpoint every two seconds; Phaser does not simulate combat and only replays
newly received server events. The frontend also loads the quest board and
starts available quests through the API. Future endpoints will cover recruiting
heroes and quest resolution.
