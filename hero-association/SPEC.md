# Hero Association

## Objective

Hero Association is a PostgreSQL-backed Quarkus Game Core for the state of an
agency and its heroes. A separate Quarkus Backend for Frontend (BFF) is the
only browser-facing service. It authenticates the browser and forwards its
server-held Keycloak access token with the existing API to Core.

## Architecture

The backend is split into independently buildable services:

- `backend/hero-association-bff`: the public API boundary on port `8080`; it
  protects browser requests with a session and CSRF, then forwards its
  server-held Keycloak access token with the current `/api/...` contract. In
  the containerized edge topology, Caddy is its only public ingress.
- `backend/hero-association-core`: the private game-state service on port
  `8081`; it validates the access token's issuer, signature, expiry, subject,
  and `hero-association-core` audience, owns PostgreSQL, and separates
  responsibilities into the following packages:

  - `domain`: JPA models for accounts, managers, agency memberships, agencies,
    heroes, parties, quests, runes, stackable items, feed posts, market orders, agency
    inventory, and equipped hero runes; it also contains the pure, non-persistent
    `domain.combat` rules engine
  - `application`: game-state use cases
  - `application.exception`: application exceptions
  - `api.v1.agency`: HTTP resource and response models for agency state
  - `api.v1.error`: HTTP error responses and exception mapping
  - `repository`: Panache repositories for game-state reads

`AgencyStateService` coordinates the initial state query. An unknown agency is
translated to `404 Not Found` at the Core API boundary. The BFF preserves that
status and response body for the browser.

## API contract

Game Core exposes agency game state and persisted rune loadouts at
`/api/v1/agencies/{agencyId}`. The BFF forwards the same public paths, so the
frontend always calls `http://localhost:8080/api/...` rather than Core.

- `GET /api/v1/account` provisions and returns the Account corresponding to
  the authenticated Keycloak subject. `POST /api/v1/account/manager` creates
  its one Manager with a unique, case-insensitive display name.
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
- `GET /api/v1/market/orders` returns the global open market order book.
- `POST /api/v1/agencies/{agencyId}/market-orders` creates and attempts to
  match a buy or sell order, returning the updated agency state.
- `DELETE /api/v1/agencies/{agencyId}/market-orders/{orderId}` cancels an open
  order, releases its remaining reservation, and returns the updated agency
  state.
- An unknown agency returns `404 Not Found` with an error message.

The response includes all five rune slots for every hero, including empty
slots. Hero class values define base health, mana, and per-second health and
mana recovery. Every five seconds, a background worker restores agency heroes'
health and mana from their elapsed time: `TRAINING` uses the base class rate
and `RESTING` uses twice that rate. Stamina recovery is not implemented yet.
Equipping and replacing runes atomically moves one rune between
the agency inventory and a hero slot. A rune that is unavailable in inventory
returns `409 Conflict`; a slot outside 0 through 4 returns `400 Bad Request`.
Agency item inventory contains stackable materials. The seed contains Magic
Crystals and Iron Ingots; item equipment and quest drops are not implemented
yet.
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
agency. Agency membership authorization is enforced; feed visibility beyond an
agency is intentionally deferred. A post can optionally reference one item stack and a positive
quantity from its agency's current inventory. The attachment is rejected when
the item or quantity is unavailable, but it does not consume or reserve items.

The initial market trades stackable materials. Creating a buy order reserves
the full gold amount at its limit price; creating a sell order reserves the
items. Compatible orders match by price and then creation time, at the resting
order's price. The buyer receives the items and the seller receives 90% of the
trade value; the remaining 10% fee is removed from the game economy for now.
Partially filled orders remain open, and cancelling an open order returns its
remaining reserved gold or items. Market-order creation and cancellation
require agency leadership. Order history and expanded item categories are
deferred.

All persistent entity IDs and API resource IDs use RFC 9562 UUID version 7
(UUIDv7). PostgreSQL stores them in native `uuid` columns. Sequential numeric
IDs must not be added for entities or exposed through the API.

## Runtime

- PostgreSQL stores game state.
- Database tables use singular entity names, including `agency`, `manager`,
  `hero`, `party`, `quest`, `quest_combat`, `quest_combatant`,
  `quest_combat_event`, `quest_combat_hit`, `rune`, `agency_rune`, `item`,
  `agency_item`, `hero_rune`, `feed_post`, and `market_order`.
- Until Flyway is introduced, application startup drops and recreates the
  schema, then loads deterministic state from `import.sql`. The seed contains
  local Accounts for the Dawnwatch and Ironridge managers, Dawnwatch Agency,
  its leader, six heroes, Broken Pass Party and its
  in-progress quest and its initial Troll combat snapshot, the available Lost
  Courier quest, rune inventory, Magic Crystals, Iron Ingots, equipped runes,
  two feed posts, Ironridge Exchange, and its open market orders. This
  development-only workflow does not retain application data.
- Local development runs the `postgres-core`, `postgres-keycloak`, and
  `postgres-bff` Docker Compose services plus Keycloak, Game Core directly on
  the host at port `8081`, and the BFF directly on the host at port `8080`.
  The Core dev profile connects to PostgreSQL at `localhost:5432`; the BFF
  session store connects at `localhost:5433`. Quarkus Dev Services is disabled
  for Core development but remains available to the test profiles.
- Keycloak runs locally at `http://localhost:8180` with a dedicated PostgreSQL
  database and imports the versioned `hero-association` realm. Compose requires
  an ignored `backend/.env` created from `backend/.env.example`; it contains
  local bootstrap, client-secret, session-state, and CSRF signing values.
- `backend/compose.caddy.yaml` is an optional full-container local HTTPS
  overlay. With `heroassociation.test` and `auth.heroassociation.test` mapped
  to `127.0.0.1`, Caddy serves the packaged frontend, proxies BFF routes at the
  first hostname, and proxies Keycloak at the second. It is intentionally not
  the default development workflow because Vite and Quarkus hot reload run
  directly on the host.
- The local Keycloak realm uses the versioned `hero-association` CSS-only login
  theme. It extends Keycloak's `keycloak.v2` theme and matches the frontend's
  dark, gold-accented visual language without replacing Keycloak templates.
- The BFF uses Keycloak's confidential authorization-code flow with PKCE. Its
  Keycloak token state is stored server-side in the BFF PostgreSQL database;
  browser sessions use an `HttpOnly`, `SameSite` cookie. `/api/v1/session` is
  public, but all proxied game routes require a BFF session and state-changing
  requests require the signed double-submit CSRF token.
- Signed-out frontend users can select **Sign in** or **Create account**. The
  latter starts Keycloak's native registration page through the protected BFF
  OIDC route, with Quarkus forwarding only the standard `prompt=create` hint while retaining
  state and PKCE ownership.
- Signing out uses OIDC RP-initiated logout. The BFF clears its local session,
  Keycloak ends the browser SSO session, and the browser returns through the
  registered, state-validated BFF post-logout callback before it is redirected
  to the frontend. Browser cookies are not cleared globally during this
  redirect because the short-lived post-logout state cookie is needed to
  validate the callback. The local Keycloak realm registers distinct callbacks
  for the normal development BFF and the isolated Playwright E2E frontend
  proxy only.
- Game Core requires a valid Keycloak bearer access token for every `/api/...`
  route. The BFF's Keycloak client mapper adds the `hero-association-core`
  audience to its access tokens before the BFF forwards them over the private
  service network.
- The first authenticated Account request provisions a UUIDv7 `account` row
  from the immutable Keycloak subject. React then requires the player to choose
  a unique Manager name before displaying the prototype. `agency_member` then
  controls access to agency state and commands; a Manager without an agency
  membership sees no shared game data. Both roles can run gameplay commands,
  while leaders alone can create or cancel market orders.
- The local Keycloak realm seeds `user1@mail.com` / `user1` and
  `user2@mail.com` / `user2` with matching Account and Manager records. Their
  seeded manager names are `User 1` and `User 2`, so local sessions and game
  data are immediately distinguishable. They must never be used outside local
  development.
- Docker Compose runs all three PostgreSQL services, Keycloak, Game Core, and
  the BFF using JVM packages by default. Only the BFF publishes port `8080`;
  Core remains on the private Compose network. Keycloak publishes port `8180`
  for its local login and admin pages. The optional Caddy overlay instead
  publishes only ports `80` and `443`; BFF, Keycloak, Core, and PostgreSQL stay
  private while the local Caddy CA provides HTTPS for the `.test` domains.
- Each service has an independent Maven fast-jar build. Native compilation is
  currently an opt-in Game Core build with `-Dnative`.
- Game Core's `Dockerfile.native` provides a `native-runtime` target that
  packages a prebuilt Linux native executable and a `native-multistage` target
  that compiles it in Docker. The native Docker Compose workflow runs that
  native Core behind the JVM BFF and PostgreSQL.

## Frontend prototype

- The independently runnable React frontend lives in `frontend`.
- The frontend starts by requesting the BFF session. Signed-out users see a
  sign-in screen; signed-in users receive no Keycloak tokens in browser storage.
  The Vite development proxy forwards both `/api` and `/auth` routes to the
  BFF so the OIDC redirect uses the BFF's `localhost:8080` callback. Its proxy
  origin is normally rewritten to the BFF target; the isolated container-browser
  E2E stack preserves the browser-visible host so its callback returns through
  Vite. That stack explicitly allowlists its Docker browser host in Vite;
  normal development keeps Vite's default host protection.
- After Account and Manager onboarding, it opens the first authorized agency
  membership through Vite's `/api`
  development proxy. If the API is unavailable, a visible notice explains that
  the UI has fallen back to a local fixture.
- The frontend needs the BFF on `http://localhost:8080` by default;
  `VITE_API_PROXY_TARGET` can override that Vite development proxy target. A
  local `VITE_API_PROXY_CHANGE_ORIGIN=false` setting preserves the original
  host for controlled OIDC callback workflows. A
  production deployment routes same-origin `/api` requests to the BFF; the
  browser never calls Game Core directly.
- Clicking the seeded in-progress quest expands an inline Phaser combat scene.
  Phaser renders the server-provided combat snapshot; it does not calculate
  attacks, recovery, spells, critical hits, or outcomes. It replays only new
  server-generated events while the scene is open. While expanded, the frontend
  calls the combat-sync command every two seconds; the backend worker continues
  combat when the view is closed.
- The frontend refreshes agency state every five seconds while its browser tab
  is visible, and immediately when the tab becomes visible again. This keeps
  agency recovery, feed posts, market orders, and background quest progress
  current without requiring WebSocket or server-sent event connections.
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
  loadout API. Item stacks can be reserved by market orders; item equipment,
  quest loot, and compatibility rules are not implemented yet.
- The Market screen loads the live global order book, creates buy or sell
  orders from the agency's item inventory, and cancels the agency's own open
  orders. It refreshes through the normal five-second browser polling.
- The Feed screen loads agency-scoped posts from the API and can publish a
  text post as the agency, its leader, or one of its heroes. A post can show
  one non-consuming agency item-stack attachment. Feed visibility and
  moderation are not implemented yet.
- The active party card displays the party name once above its hero cards;
  each active party hero displays their stamina.

## Verification

Run the Game Core test suite with:

```bash
cd hero-association/backend/hero-association-core
./mvnw test
```

Run the BFF test suite with:

```bash
cd hero-association/backend/hero-association-bff
./mvnw test
```
