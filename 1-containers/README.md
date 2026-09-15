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
