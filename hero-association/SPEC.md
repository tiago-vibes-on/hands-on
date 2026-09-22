# Hero Association

## Objective

Hero Association is a PostgreSQL-backed Quarkus backend for the game state of
an agency and its heroes.

## Architecture

The backend separates responsibilities into the following packages:

- `domain`: JPA models for managers, agencies, heroes, parties, quests, runes,
  agency inventory, and equipped hero runes
- `application`: game-state use cases
- `application.exception`: application exceptions
- `api.v1.agency`: HTTP resource and response models for agency state
- `api.v1.error`: HTTP error responses and exception mapping
- `repository`: Panache repositories for game-state reads

`AgencyStateService` coordinates the initial state query. An unknown agency is
translated to `404 Not Found` at the API boundary.

## API contract

The initial API exposes agency game state and persisted rune loadouts at
`/api/v1/agencies/{agencyId}`.

- `GET /api/v1/agencies/{agencyId}/state` returns an agency, its leader and
  upgrade levels, heroes, parties and quests, rune inventory, and hero rune
  slots.
- `PUT /api/v1/agencies/{agencyId}/heroes/{heroId}/rune-slots/{slotIndex}`
  equips the requested available rune in a slot and returns the updated agency
  state.
- `DELETE /api/v1/agencies/{agencyId}/heroes/{heroId}/rune-slots/{slotIndex}`
  unequips a rune and returns the updated agency state.
- `PUT /api/v1/agencies/{agencyId}/heroes/{heroId}/activity` changes an
  agency hero's activity to `TRAINING` or `RESTING` and returns the updated
  agency state.
- `POST /api/v1/agencies/{agencyId}/parties` creates a named prepared party
  and returns the updated agency state.
- `PUT /api/v1/agencies/{agencyId}/parties/{partyId}/heroes/{heroId}` adds an
  available agency hero to a prepared party and returns the updated agency
  state.
- `DELETE /api/v1/agencies/{agencyId}/parties/{partyId}/heroes/{heroId}`
  removes a hero from a prepared party and returns the updated agency state.
- An unknown agency returns `404 Not Found` with an error message.

The response includes all five rune slots for every hero, including empty
slots. Hero class values define base health, mana, and per-second health and
mana recovery. Equipping and replacing runes atomically moves one rune between
the agency inventory and a hero slot. A rune that is unavailable in inventory
returns `409 Conflict`; a slot outside 0 through 4 returns `400 Bad Request`.
Agency levels are Agency, Training, Rest, Size, Reputation, and Intelligence.
Rest is the single agency upgrade responsible for hero recovery; there is no
Medical Level. Quest heroes cannot change their agency activity and return
`409 Conflict`.
Prepared-party members remain at the agency and retain their `TRAINING` or
`RESTING` activity until a quest starts. Party membership cannot change while
the party is on an in-progress quest, and a quest hero cannot be moved into a
prepared party; both return `409 Conflict`. Party names must be unique within
an agency. Other game actions are still being specified.

All persistent entity IDs and API resource IDs use RFC 9562 UUID version 7
(UUIDv7). PostgreSQL stores them in native `uuid` columns. Sequential numeric
IDs must not be added for entities or exposed through the API.

## Runtime

- PostgreSQL stores game state.
- Database tables use singular entity names, including `agency`, `manager`,
  `hero`, `party`, `quest`, `rune`, `agency_rune`, and `hero_rune`.
- Until Flyway is introduced, application startup drops and recreates the
  schema, then loads deterministic state from `import.sql`. The seed contains
  Dawnwatch Agency, its leader, six heroes, Broken Pass Party, an in-progress
  quest, rune inventory, and equipped runes. This development-only workflow
  does not retain application data.
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
- On startup, it loads the seeded Dawnwatch Agency UUID through Vite's `/api`
  development proxy. If the API is unavailable, a visible notice explains that
  the UI has fallen back to a local fixture.
- The frontend needs the Quarkus API on `http://localhost:8080` by default;
  `VITE_API_PROXY_TARGET` can override that Vite development proxy target. A
  production deployment needs a same-origin `/api` reverse proxy or gateway.
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
- The Heroes screen separates the active quest's named party, prepared
  parties, and unassigned heroes at the agency. Agency heroes can persistently
  switch between Training and Resting, without numeric training stats.
  Prepared parties can be named, filled, and changed through the backend;
  their members retain their agency activity until a quest starts.
- A Resting agency hero is described as recovering stamina, health, and mana
  at twice the normal rate. Timed recovery is not implemented yet.
- The combat prototype recovers hero health and mana once per second. Warrior
  recovery is 10 health and 2 mana; Mage recovery is 2 health and 10 mana;
  Archer recovery is 6 health and 6 mana. Agency Rest is intended to use twice
  those base rates when timed agency recovery is implemented.
- Party members are shown as earning experience from creatures.
- The stamina display is green at 80% or more, yellow from 30% through 79%,
  and red below 30%. The matching experience gain is 150%, 100%, and 50%.
- Every hero card ends with five rune slots loaded from the API. Heroes with
  learned spells also show their spell slots; currently only Elara has the two
  mage spells. Critical Chance and Critical Damage Runes affect local combat;
  other rune stat effects do not yet change gameplay.
- Combat displays five read-only rune slots for each hero so their equipped
  loadout is visible. Creatures do not display rune slots.
- The initial quest party equips a Critical Chance Rune on every hero, and
  Elara also equips a Critical Damage Rune. Each initial troll has a 10%
  critical-hit chance.
- The Agency screen shows the API-loaded rune inventory with quantities,
  stats, and descriptions. Clicking a hero rune slot opens a rune drawer that
  persists equipping, replacing, and removing an available rune through the
  loadout API. Compatibility rules are not implemented yet.
- The active party card displays the party name once above its hero cards;
  each active party hero displays their stamina.

## Verification

Run the backend test suite with:

```bash
cd hero-association/backend
./mvnw test
```
