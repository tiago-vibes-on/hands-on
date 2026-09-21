# Hero Association

## Objective

Hero Association is a PostgreSQL-backed Quarkus backend for registering and
managing heroes.

## Architecture

The backend separates responsibilities into the following packages:

- `domain`: the `Hero` model, which is also the JPA entity
- `application`: hero use cases
- `application.exception`: application exceptions
- `api.v1.hero`: HTTP resources and hero request and response models
- `api.v1.error`: HTTP error responses and exception mapping
- `repository`: the Panache repository

`HeroApplicationService` coordinates use cases. Application exceptions are
translated to HTTP responses at the API boundary.

## API contract

The API is exposed only at `/api/v1/heroes`.

- `POST /api/v1/heroes` creates a hero and returns `201 Created`.
- `GET /api/v1/heroes` lists heroes.
- `GET /api/v1/heroes/{id}` returns a hero or `404 Not Found`.
- `PUT /api/v1/heroes/{id}` replaces all mutable fields.
- `PATCH /api/v1/heroes/{id}` changes a non-empty subset of mutable fields;
  an empty update returns `400 Bad Request`.
- `DELETE /api/v1/heroes/{id}` removes a hero and returns `204 No Content`.

Each hero has generated `id`, `name`, `alias`, and `power`. The mutable fields
are required for creation and replacement. Aliases are unique; duplicate
aliases return `409 Conflict`.

## Runtime

- PostgreSQL stores hero data.
- Database tables use singular entity names; hero records are stored in the
  `hero` table.
- PostgreSQL can run independently through Docker Compose and is exposed to a
  host-run microservice at `localhost:5432`.
- Docker Compose runs the backend and PostgreSQL using the JVM package by
  default.
- Maven produces a JVM fast-jar by default.
- Native compilation is available as an opt-in Maven build with `-Dnative`; it
  produces a GraalVM-compatible native executable and does not change the
  default Maven or Docker Compose workflow.
- `Dockerfile.native` provides a `native-runtime` target that packages the
  prebuilt Linux native executable and a `native-multistage` target that
  compiles it in Docker. The native Docker Compose workflow uses the runtime
  target and PostgreSQL.

## Verification

Run the backend test suite with:

```bash
cd hero-association/backend
./mvnw test
```
