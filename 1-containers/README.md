# Containers

## Objective

Containerize a small Quarkus Hero Association service, add PostgreSQL persistence, and evolve the container image from a single-stage build to a multi-stage build.

## Project structure

- `initial`: a stateless Quarkus service used as the starting point.
- `final`: the PostgreSQL-backed Hero Association API, ready to run with Docker Compose.
- `SPEC.md`: the scope and acceptance criteria for this project.

## What this project covers

- Running a Java application in a Docker container.
- Running an application and PostgreSQL together with Docker Compose.
- Persisting hero registrations in PostgreSQL.
- Comparing a single-stage container build with a multi-stage build.

## Prerequisites

- Java 25 LTS
- Maven
- Docker with Docker Compose

## Application model

The final application manages heroes with the following fields:

```text
id
name
alias
power
```

## Checkpoints

The `initial` and `final` directories are independently runnable.

### Initial

```bash
cd initial
./mvnw quarkus:dev
```

The application is available at `http://localhost:8080/status`.

### Final

The default `Dockerfile` is the multi-stage image. Build it explicitly from
the `final` directory:

```bash
cd final
docker build -f Dockerfile -t hero-association:multi-stage .
```

Run the multi-stage image with PostgreSQL using Docker Compose:

```bash
docker compose up --build
```

The API is available at `http://localhost:8080/heroes`.

### API examples

Create Darth Vader:

```bash
curl --request POST http://localhost:8080/heroes \
  --header 'Content-Type: application/json' \
  --data '{
    "name": "Anakin Skywalker",
    "alias": "Darth Vader",
    "power": "The Force"
  }'
```

Use the `id` returned by the creation request in the following examples. Patch
only Darth Vader's power:

```bash
curl --request PATCH http://localhost:8080/heroes/1 \
  --header 'Content-Type: application/json' \
  --data '{
    "power": "The Force and lightsaber combat"
  }'
```

Replace all mutable fields with `PUT`:

```bash
curl --request PUT http://localhost:8080/heroes/1 \
  --header 'Content-Type: application/json' \
  --data '{
    "name": "Anakin Skywalker",
    "alias": "Darth Vader",
    "power": "The Force and lightsaber combat"
  }'
```

The PostgreSQL credentials in `compose.yaml` are local-development values only. To stop the stack while keeping hero data, run:

```bash
docker compose down
```

To remove the PostgreSQL volume as well, run:

```bash
docker compose down --volumes
```

### Single-stage image

The final checkpoint keeps `Dockerfile.single-stage` as the intermediate,
single-stage example. Build it from the `final` directory:

```bash
docker build -f Dockerfile.single-stage -t hero-association:single-stage .
```

Start PostgreSQL with Docker Compose, then run the single-stage image on the
same Compose network:

```bash
docker compose up -d postgres
docker run --rm --network hero-association_default -p 8080:8080 \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres:5432/hero_association \
  -e QUARKUS_DATASOURCE_USERNAME=hero_association \
  -e QUARKUS_DATASOURCE_PASSWORD=hero_association \
  hero-association:single-stage
```

The API is available at `http://localhost:8080/heroes`. Press `Ctrl-C` to stop
the API container, then run `docker compose down` to stop PostgreSQL.
