# Hero Association Backend

The Hero Association backend is a Quarkus API backed by PostgreSQL. Its first
game-state endpoint exposes an agency, its leader, heroes, party and quest,
rune inventory, and equipped rune slots.

## Prerequisites

- Java 25 LTS
- Docker with Docker Compose

## Test

From this directory, run:

```bash
./mvnw test
```

The test suite starts PostgreSQL automatically through Quarkus Dev Services.

## Run the microservice locally

Run the API directly on the host, without putting the microservice in a
container:

```bash
./mvnw quarkus:dev
```

Quarkus Dev Services starts and removes a temporary PostgreSQL container when
no datasource connection settings are supplied.

### Development database reset

Until Flyway is introduced, every application startup drops and recreates the
database schema, then loads deterministic game data from
`src/main/resources/import.sql`. The seed contains Dawnwatch Agency, its
leader, six heroes, Broken Pass Party, an in-progress troll quest, seven rune
definitions, agency inventory, and the party's equipped runes. Do not use this
configuration with data that must be retained.

### Run PostgreSQL separately

To keep the database running independently of the microservice, start only the
PostgreSQL service:

```bash
docker compose up --detach postgres
```

The database is exposed at `localhost:5432`. Connect the host-run
microservice to it with:

```bash
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/hero_association \
QUARKUS_DATASOURCE_USERNAME=hero_association \
QUARKUS_DATASOURCE_PASSWORD=hero_association \
./mvnw quarkus:dev
```

Stop the standalone database while preserving its data:

```bash
docker compose stop postgres
```

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

It returns the agency and leader, Agency, Training, Rest, Size, Reputation, and
Intelligence upgrade levels, all heroes and their class recovery values,
parties with quests and member IDs, agency rune inventory, and each hero's five
rune slots. Rest is the single upgrade for hero recovery; there is no Medical
Level. Equipping or replacing a rune decrements its
agency inventory quantity and returns any replaced rune to inventory in the
same transaction. An unknown agency or hero returns `404 Not Found`; an
unavailable rune returns `409 Conflict`. Agency heroes can switch between
`TRAINING` and `RESTING`; a hero on a quest cannot change activity and returns
`409 Conflict`. A manager can create a uniquely named prepared party and add
or remove available agency heroes. Prepared members keep their activity until
a quest starts. An in-progress quest party cannot have its membership changed,
and a hero already on a quest cannot move to another party; both return `409
Conflict`.

Read the seeded state:

```bash
curl http://localhost:8080/api/v1/agencies/019c4c00-0001-7000-8000-000000000001/state
```

The React frontend loads this endpoint when it starts and persists rune drawer,
agency-activity, and prepared-party changes through the API. Combat simulation
remains local. Future endpoints will cover recruiting heroes and starting
quests.
