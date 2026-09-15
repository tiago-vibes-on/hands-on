# DDD and Exception Handling

## Objective

Restructure the Hero Association microservice around clear domain, application, API, and repository responsibilities. Translate application errors into HTTP responses at the API boundary while preserving the Docker and native-image workflows established in `2-java-native-images`.

## Technology

- Java 25 LTS
- Quarkus LTS
- Maven
- PostgreSQL
- Docker and Docker Compose
- GraalVM for JDK 25 with Native Image, for local native builds

## Initial state

- An independent copy of `2-java-native-images/final`.
- PostgreSQL-backed Hero Association API exposed at `/heroes`.
- JVM builds are the default; native builds are explicit.
- Docker Compose supports JVM execution, host-native execution with Docker PostgreSQL, and a native Docker image.

## Final state

- The application is organized into `domain`, `application`, `api`, and `repository` packages.
- `Hero` remains the single model and is also the JPA entity.
- `HeroApplicationService` coordinates hero use cases.
- `HeroRepository` remains a direct Panache repository in the `repository` package.
- The versioned HTTP API is exposed only at `/api/v1/heroes`.
- A hero alias is unique.
- Application exceptions are translated into `404 Not Found` and `409 Conflict` responses by the API layer.

## API behavior

- `POST /api/v1/heroes` creates a hero and returns `201 Created`.
- `GET /api/v1/heroes` lists heroes.
- `GET /api/v1/heroes/{id}` returns a hero or `404 Not Found`.
- `PUT /api/v1/heroes/{id}` replaces all mutable fields and requires `name`, `alias`, and `power`.
- `PATCH /api/v1/heroes/{id}` changes a non-empty subset of mutable fields.
- `POST`, `PUT`, and `PATCH` return `409 Conflict` when the requested alias belongs to another hero.
- IDs are generated and cannot be changed.

## Out of scope

- A separate persistence entity or mapping layer
- Repository interfaces in the domain
- Ports-and-adapters package ceremony
- New model fields or endpoints beyond API versioning
- Authentication and authorization
- Database migrations

## Acceptance criteria

- `initial` and `final` build and run independently.
- `initial` retains the behavior of `2-java-native-images/final`.
- `final` passes automated endpoint tests, including unique-alias behavior.
- `final` exposes only `/api/v1/heroes`.
- Default Maven and Docker builds use the JVM.
- The final checkpoint retains the opt-in local-native and Docker-native workflows.
