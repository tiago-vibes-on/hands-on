# Hero Association Backend

The Hero Association backend is a Quarkus API backed by PostgreSQL. It manages
hero registrations with a unique alias.

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

Stop it with:

```bash
docker compose -f compose.native.yaml down
```

The API is available at `http://localhost:8080/api/v1/heroes`.

To stop the containers while retaining hero data:

```bash
docker compose down
```

To also remove the local PostgreSQL volume:

```bash
docker compose down --volumes
```

## API

A hero has `id`, `name`, `alias`, and `power`. `name`, `alias`, and `power`
are required, and aliases must be unique.

- `POST /api/v1/heroes`
- `GET /api/v1/heroes`
- `GET /api/v1/heroes/{id}`
- `PUT /api/v1/heroes/{id}`
- `PATCH /api/v1/heroes/{id}`
- `DELETE /api/v1/heroes/{id}`

Create a hero:

```bash
curl --request POST http://localhost:8080/api/v1/heroes \
  --header 'Content-Type: application/json' \
  --data '{
    "name": "Anakin Skywalker",
    "alias": "Darth Vader",
    "power": "The Force"
  }'
```
