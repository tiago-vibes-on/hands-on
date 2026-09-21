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
- Damage popups cycle through three horizontal lanes above each target and
  drift outward to keep closely timed hits readable. Basic damage is gold and
  magic damage is purple.
- There is no base critical-hit chance. A Critical Chance Rune adds 1% critical
  chance, and a Critical Damage Rune adds 10 percentage points to the
  critical-damage multiplier: 200% damage becomes 210%. A critical hit shakes
  the target and uses a larger highlighted damage popup. These rune effects
  apply when a new mock encounter is started.
- Elara Moonweaver is Magic Level 15 and has two displayed mage spell slots.
  Fire Ball costs 20 mana, deals `10 + 150% of Magic Level` to one creature,
  and has a three-second cooldown. Lightning Rail costs 40 mana, deals
  `2 + 80% of Magic Level` to every living creature, and has a five-second
  cooldown. The combat prototype auto-casts an available spell when its
  cooldown is ready and Elara has sufficient mana. A dark radial overlay
  clears from right to left across a spell icon to visualize its independent
  cooldown.
- The mock Heroes screen separates the active quest's named party from heroes
  at the agency. Agency heroes show a Training or Resting activity without
  numeric stats. A party can contain one or more heroes. Assignment is
  frontend-only and not persisted.
- A Resting agency hero is described as recovering stamina, health, and mana
  at twice the normal rate. Timed recovery is not implemented yet.
- The combat prototype recovers hero health and mana once per second. Warrior
  recovery is 10 health and 2 mana; Mage recovery is 2 health and 10 mana;
  Archer recovery is 6 health and 6 mana. Agency Rest is intended to use twice
  those base rates when timed agency recovery is implemented.
- Party members are shown as earning experience from creatures.
- The stamina display is green at 80% or more, yellow from 30% through 79%,
  and red below 30%. The matching experience gain is 150%, 100%, and 50%.
- Every mock hero card ends with five rune slots. Heroes with learned spells
  also show their spell slots; currently only Elara has the two mage spells.
  Critical Chance and Critical Damage Runes affect mock combat; other rune
  stat effects do not yet change gameplay.
- Combat displays five read-only rune slots for each hero so their equipped
  loadout is visible. Creatures do not display rune slots.
- The initial quest party equips a Critical Chance Rune on every hero, and
  Elara also equips a Critical Damage Rune. Each initial troll has a 10%
  critical-hit chance.
- The mock Agency screen shows a rune inventory with quantities, stats, and
  descriptions. Clicking a hero rune slot opens a rune drawer that lets any
  available agency rune fill that slot. Replacing or removing a rune returns
  the previous rune to agency inventory.
- The active party card displays the party name once above its hero cards;
  each active party hero displays their stamina.

## Verification

Run the backend test suite with:

```bash
cd hero-association/backend
./mvnw test
```
