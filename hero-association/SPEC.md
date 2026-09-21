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
- Until Flyway is introduced, application startup drops and recreates the
  schema, then loads deterministic seed heroes from `import.sql`. This
  development-only workflow does not retain application data.
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

## Frontend prototype

- The independently runnable React frontend lives in `frontend`.
- It currently uses mock data and does not call this API.
- Clicking the in-progress quest expands an inline Phaser combat scene. The
  scene is a frontend prototype; it does not yet persist or resolve quests in
  the backend.
- The combat prototype starts the three heroes at Level 1 and displays their
  current health and mana beside their respective bars. Placeholder trolls
  also display a 100-mana bar.
- Resource values appear to the left of hero bars and to the right of creature
  bars.
- Hero resource bars empty from the right; creature resource bars empty from
  the left.
- Each combatant has two empty visual spell slots; spell behavior is not yet
  implemented.
- The mock Heroes screen separates the active quest's named party from heroes
  at the agency. Agency heroes show a Training or Resting activity without
  numeric stats. A party can contain one or more heroes. Assignment is
  frontend-only and not persisted.
- A Resting agency hero is described as recovering stamina, health, and mana
  at twice the normal rate. Timed recovery is not implemented yet.
- Party members are shown as earning experience from creatures.
- The stamina display is green at 80% or more, yellow from 30% through 79%,
  and red below 30%. The matching experience gain is 150%, 100%, and 50%.
- Every mock hero card ends with five empty item slots and two empty spell
  slots. They do not yet change gameplay.
- The active party card displays the party name once above its hero cards;
  each active party hero displays their stamina.

## Verification

Run the backend test suite with:

```bash
cd hero-association/backend
./mvnw test
```
