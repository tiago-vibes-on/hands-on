# DDD and Exception Handling

## Objective

Organize the Hero Association microservice by responsibility without adding unnecessary architectural layers. The final checkpoint introduces a versioned API, separates the domain model, application use cases, HTTP API, and repository, and translates application errors into HTTP responses at the API boundary.

## Project structure

- `initial`: an independent copy of the final Native Images checkpoint.
- `final`: the same service organized around DDD-inspired responsibilities.
- `SPEC.md`: scope and acceptance criteria.

## Final application structure

```text
domain
application
api/v1
repository
```

```text
HeroController -> HeroApplicationService -> HeroRepository
                         |
                       Hero
```

`HeroApplicationService` raises application exceptions for an unknown hero or a duplicate alias. `ApiExceptionMapper` translates them to `404 Not Found` and `409 Conflict`, keeping HTTP details out of the application layer.

The final application deliberately has one `Hero` model. It is both the domain model and the JPA entity; a separate `HeroEntity` would duplicate the same data without a current benefit.

## Application model

```text
id
name
alias
power
```

Each alias is unique.

## Prerequisites

- Java 25 LTS
- Maven
- Docker with Docker Compose
- GraalVM for JDK 25 with Native Image, for local native builds

JVM builds are the default. Native builds are selected explicitly.

## Checkpoints

### Initial

```bash
cd initial
mvn test
docker compose up --build
```

The API is available at `http://localhost:8080/heroes`.

### Final: JVM application in Docker

```bash
cd final
mvn test
docker compose up --build
```

The API is available at `http://localhost:8080/api/v1/heroes`.

### Final: host-native executable with Docker PostgreSQL

```bash
cd final
docker compose up -d postgres
mvn package -Dnative -DskipTests
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/hero_association \
QUARKUS_DATASOURCE_USERNAME=hero_association \
QUARKUS_DATASOURCE_PASSWORD=hero_association \
./target/hero-association-1.0.0-SNAPSHOT-runner
```

### Final: native application in Docker

```bash
cd final
docker compose -f compose.yaml -f compose.native.yaml up --build
```

To stop a Compose setup:

```bash
docker compose down
```
