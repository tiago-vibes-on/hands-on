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

```bash
cd final
docker compose up --build
```

The API is available at `http://localhost:8080/heroes`.

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
the API container, then run `docker compose down` to stop PostgreSQL. The
default `Dockerfile` remains the multi-stage image used by `docker compose up
--build`.
