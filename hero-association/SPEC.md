# Hero Association

## Objective

Hero Association is a PostgreSQL-backed Quarkus backend for the game state of
an agency and its heroes.

## Architecture

The backend separates responsibilities into the following packages:

- `domain`: JPA models for managers, agencies, heroes, parties, quests, runes,
  stackable items, feed posts, agency inventory, and equipped hero runes; it
  also contains the pure, non-persistent `domain.combat` rules engine
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
  upgrade levels, heroes, parties and quests, rune and item inventory, hero
  rune slots, feed posts, and any persisted quest-combat snapshot.
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
- `PUT /api/v1/agencies/{agencyId}/quests/{questId}/start` starts an available
  quest with the `partyId` in its request body and returns the updated agency
  state.
- `POST /api/v1/agencies/{agencyId}/quests/{questId}/combat/sync` advances an
  existing combat snapshot by elapsed wall time and returns the updated agency
  state.
- `POST /api/v1/agencies/{agencyId}/feed-posts` creates an agency-scoped text
  post and returns the updated agency state.
- An unknown agency returns `404 Not Found` with an error message.

The response includes all five rune slots for every hero, including empty
slots. Hero class values define base health, mana, and per-second health and
mana recovery. Every five seconds, a background worker restores agency heroes'
health and mana from their elapsed time: `TRAINING` uses the base class rate
and `RESTING` uses twice that rate. Stamina recovery is not implemented yet.
Equipping and replacing runes atomically moves one rune between
the agency inventory and a hero slot. A rune that is unavailable in inventory
returns `409 Conflict`; a slot outside 0 through 4 returns `400 Bad Request`.
Agency item inventory currently supports read-only stackable materials. The
seed contains Magic Crystals and Iron Ingots; item equipment, quest drops,
and market reservations are not implemented yet.
Agency levels are Agency, Training, Rest, Size, Reputation, and Intelligence.
Rest represents the agency's recovery facilities; there is no Medical Level.
Its concrete upgrade effect is still to be defined. Quest heroes cannot change
their agency activity and return `409 Conflict`.
Prepared-party members remain at the agency and retain their `TRAINING` or
`RESTING` activity until a quest starts. Party membership cannot change while
the party is on an in-progress quest, and a quest hero cannot be moved into a
prepared party; both return `409 Conflict`. Party names must be unique within
an agency. Other game actions are still being specified.
Quest definitions include a description, creature objective, party-size range,
duration estimate, and gold reward. Starting a quest requires a prepared party
whose member count is inside that quest's range. It changes the quest to
`IN_PROGRESS`, links it to the party, and changes every party member to
`ON_QUEST`. It persists `startedAt` and `expectedCompletionAt`, calculated
from the quest duration. A quest that is not `AVAILABLE` returns `409
Conflict`; an ineligible party size returns `400 Bad Request`. Timed
progression and combat resolution are not exposed or persisted on the backend
yet. The internal combat engine is deterministic: callers advance a supplied
combat time and supply its random source. It resolves independent basic-attack
timers, hero health and mana recovery, mage spell cooldowns and mana costs,
critical hits, deaths, and battle completion. The seeded Troll quest has a
persisted, API-visible combat snapshot with every combatant's resources,
statistics, and next action times. It also retains the latest 100
server-generated combat events, including actions, recovery, mana costs, hits,
criticals, and defeats. A combat sync command restores that snapshot into the
engine, advances it by the time since its previous sync, and persists the
result and any new events atomically. A background worker uses the same
operation every five seconds for all active snapshots. `GET /state` is
read-only and does not advance combat. Newly started quests do not create a
snapshot yet. Each synchronization also persists the current health and mana
of heroes in the encounter; stamina, rewards, and death resolution are not
implemented yet.
Feed posts are limited to 500 characters. An agency can post as itself, its
single current leader, or one of its heroes; the author must belong to the
agency. Authentication and feed visibility beyond an agency are intentionally
deferred. A post can optionally reference one item stack and a positive
quantity from its agency's current inventory. The attachment is rejected when
the item or quantity is unavailable, but it does not consume or reserve items.

All persistent entity IDs and API resource IDs use RFC 9562 UUID version 7
(UUIDv7). PostgreSQL stores them in native `uuid` columns. Sequential numeric
IDs must not be added for entities or exposed through the API.

## Runtime

- PostgreSQL stores game state.
- Database tables use singular entity names, including `agency`, `manager`,
  `hero`, `party`, `quest`, `quest_combat`, `quest_combatant`,
  `quest_combat_event`, `quest_combat_hit`, `rune`, `agency_rune`, `item`,
  `agency_item`, `hero_rune`, and `feed_post`.
- Until Flyway is introduced, application startup drops and recreates the
  schema, then loads deterministic state from `import.sql`. The seed contains
  Dawnwatch Agency, its leader, six heroes, Broken Pass Party and its
  in-progress quest and its initial Troll combat snapshot, the available Lost
  Courier quest, rune inventory, Magic Crystals, Iron Ingots, equipped runes,
  and two feed posts. This development-only workflow does not retain
  application data.
- Local development runs PostgreSQL through the `postgres` Docker Compose
  service and Quarkus directly on the host with `./mvnw quarkus:dev`. The dev
  profile connects to that database at `localhost:5432`; Quarkus Dev Services
  is disabled for development but remains available to the test profile.
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
- Clicking the seeded in-progress quest expands an inline Phaser combat scene.
  Phaser renders the server-provided combat snapshot; it does not calculate
  attacks, recovery, spells, critical hits, or outcomes. It replays only new
  server-generated events while the scene is open. While expanded, the frontend
  calls the combat-sync command every two seconds; the backend worker continues
  combat when the view is closed.
- The frontend refreshes agency state every five seconds while its browser tab
  is visible, and immediately when the tab becomes visible again. This keeps
  agency recovery, feed posts, and background quest progress current without
  requiring WebSocket or server-sent event connections.
- The combat prototype starts the three heroes at Level 1 and displays their
  current health and mana beside their respective bars. Placeholder trolls
  also display a 100-mana bar.
- Resource values appear to the left of hero bars and to the right of creature
  bars.
- Hero resource bars empty from the right; creature resource bars empty from
  the left.
- There is no base critical-hit chance. A Critical Chance Rune adds 1% critical
  chance, and a Critical Damage Rune adds 10 percentage points to the
  critical-damage multiplier: 200% damage becomes 210%. These values are in
  the server combat snapshot and are applied by the server engine.
- Elara Moonweaver is Magic Level 15 and has two displayed mage spell slots.
  Fire Ball costs 20 mana, deals `10 + 150% of Magic Level` to one creature,
  and has a three-second cooldown. Lightning Rail costs 40 mana, deals
  `2 + 80% of Magic Level` to every living creature, and has a five-second
  cooldown. The server engine auto-casts an available spell when its cooldown
  is ready and Elara has sufficient mana. A dark radial overlay clears from
  right to left across a spell icon to visualize the cooldown reported in the
  latest snapshot.
- The Heroes screen separates the active quest's named party, prepared
  parties, and unassigned heroes at the agency. Multiple active parties are
  displayed independently. Agency heroes can persistently switch between
  Training and Resting, without numeric training stats.
  Prepared parties can be named, filled, and changed through the backend;
  their members retain their agency activity until a quest starts.
- The Quests screen reads available and active quests from the API. A manager
  can select a prepared party and start an eligible available quest. The Phaser
  combat view remains attached only to the initially seeded active quest, but
  it renders and synchronizes its server snapshot. Newly started quests do not
  yet create combat snapshots.
- A background worker restores agency hero health and mana every five seconds
  from elapsed full seconds. Training uses the base class rate and Resting uses
  twice that rate. Stamina recovery is not implemented yet.
- The server combat engine recovers hero health and mana once per second. Warrior
  recovery is 10 health and 2 mana; Mage recovery is 2 health and 10 mana;
  Archer recovery is 6 health and 6 mana.
- Party members are shown as earning experience from creatures.
- The stamina display is green at 80% or more, yellow from 30% through 79%,
  and red below 30%. The matching experience gain is 150%, 100%, and 50%.
- Every hero card ends with five rune slots loaded from the API. Heroes with
  learned spells also show their spell slots; currently only Elara has the two
  mage spells. Critical Chance and Critical Damage Runes affect server combat;
  other rune stat effects do not yet change gameplay.
- Combat displays five read-only rune slots for each hero so their equipped
  loadout is visible. Creatures do not display rune slots.
- The initial quest party equips a Critical Chance Rune on every hero, and
  Elara also equips a Critical Damage Rune. Each initial troll has a 10%
  critical-hit chance.
- The Agency screen shows API-loaded stackable items and runes with quantities
  and descriptions. Clicking a hero rune slot opens a rune drawer that
  persists equipping, replacing, and removing an available rune through the
  loadout API. Item stacks are read-only until quest loot and market commands
  are introduced; compatibility rules are not implemented yet.
- The Feed screen loads agency-scoped posts from the API and can publish a
  text post as the agency, its leader, or one of its heroes. A post can show
  one non-consuming agency item-stack attachment. Feed visibility and
  moderation are not implemented yet.
- The active party card displays the party name once above its hero cards;
  each active party hero displays their stamina.

## Verification

Run the backend test suite with:

```bash
cd hero-association/backend
./mvnw test
```
