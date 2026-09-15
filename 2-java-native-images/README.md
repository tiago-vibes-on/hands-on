# Java Native Images

## Objective

Turn the PostgreSQL-backed Hero Association application from the Containers project into a Quarkus native executable. Run the application directly on WSL with GraalVM, then build and run it as a native Docker image.

## Project structure

- `initial`: the JVM-based Hero Association application from the final Containers checkpoint.
- `final`: the same application with opt-in local and Docker-native build paths.
- `SPEC.md`: scope and acceptance criteria.

## What this project covers

- Building a Quarkus native executable locally with GraalVM.
- Running a native executable outside Docker while PostgreSQL runs in Docker.
- Building a native executable inside a Docker multi-stage build.
- Running the native API and PostgreSQL together with Docker Compose.
- Comparing JVM and native image size and startup behavior on your own machine.

## Prerequisites

- Java 25 LTS
- Maven
- Docker with Docker Compose
- GraalVM for JDK 25 with Native Image, for the local-native path

Use WSL for the local GraalVM path. The Docker-native path includes Mandrel in its builder image and does not require GraalVM to be installed locally.

The default Maven and Docker builds use the JVM. Native compilation is always explicit.

`mvn package -Dnative` is intentionally a local-only build: it fails if GraalVM Native Image is not available. It never falls back to Docker.

## Application model

```text
id
name
alias
power
```

## Checkpoints

### Initial: JVM application in Docker

```bash
cd initial
mvn test
docker compose up --build
```

The API is available at `http://localhost:8080/heroes`.

### Final: default JVM application in Docker

```bash
cd final
docker compose up --build
```

### Final: local native executable with Docker PostgreSQL

First start only PostgreSQL:

```bash
cd final
docker compose up -d postgres
```

Build the native executable with local GraalVM:

```bash
mvn package -Dnative -DskipTests
```

Run it directly on WSL:

```bash
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/hero_association \
QUARKUS_DATASOURCE_USERNAME=hero_association \
QUARKUS_DATASOURCE_PASSWORD=hero_association \
./target/hero-association-1.0.0-SNAPSHOT-runner
```

The native API is available at `http://localhost:8080/heroes`.

### Final: native application in Docker

Stop the locally run executable, then use the native Compose override:

```bash
docker compose -f compose.yaml -f compose.native.yaml up --build
```

This explicitly selects `Dockerfile.native`, which compiles the native executable in the Docker build stage and starts the native API with PostgreSQL.

To stop either Compose setup while retaining data:

```bash
docker compose down
```

To remove the local PostgreSQL data too:

```bash
docker compose down --volumes
```
