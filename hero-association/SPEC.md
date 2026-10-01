# Hero Association

## Objective

Hero Association is a PostgreSQL-backed Quarkus Game Core for the state of an
agency and its heroes. A separate Quarkus Backend for Frontend (BFF) is the
only browser-facing service. It authenticates the browser and forwards its
server-held Keycloak access token with the existing API to Core.

## Architecture

Game Core is a temporary home for multiple game domains while their ownership
and behavior are established. New domain logic should have clear internal
boundaries; cohesive domains such as Market are intended to become separate
services later, with their own data and reliable cross-domain contracts. See
[ADR 0004](adr/0004-core-as-temporary-modular-monolith.md). This is an
architecture direction, not an implemented service split.

The backend is split into independently buildable services:

- `backend/hero-association-bff`: the public API boundary on port `8080`; it
  protects browser requests with a session and CSRF, then forwards its
  server-held Keycloak access token with the current `/api/...` contract. In
  full k3d and hybrid development, Envoy Gateway is its public ingress and
  externally authorizes market placement
  against the BFF before enforcing the sole per-user gateway limit.
- `backend/hero-association-core`: the private game-state service on port
  `8081`; it validates the access token's issuer, signature, expiry, subject,
  and `hero-association-core` audience, owns PostgreSQL, and separates
  responsibilities into the following packages:

  - `domain`: JPA models for accounts, managers, agency memberships, agencies,
    heroes, parties, quests, runes, stackable items, feed posts, market orders, agency
    inventory, and equipped hero runes; it consumes the pure, non-persistent
    `hero-association-lib/combat-engine` rules engine
  - `application`: game-state use cases
  - `application.exception`: application exceptions
  - `api.v1.agency`: HTTP resource and response models for agency state
  - `api.v1.error`: HTTP error responses and exception mapping
  - `repository`: Panache repositories for game-state reads

`AgencyStateService` coordinates the initial state query. An unknown agency is
translated to `404 Not Found` at the Core API boundary. The BFF preserves that
status and response body for the browser.

## Local build validation

The local pipeline tests Core, BFF, and Expedition against disposable k3d
PostgreSQL, Redis, and RabbitMQ before packaging their JVM images with
`-DskipTests`; frontend lint and build stay local. The images are saved in one
checksummed archive. Candidate builds require all four daily application
services in full k3d mode and never promote them automatically. Direct Maven
builds remain independent and may use local Dev Services or Testcontainers.
Before deployment, the disposable k3d E2E gate must run those exact images,
verify every application Pod image ID against the archive, and pass browser,
session, isolated Core-cache fallback, BFF outage, and market k6 checks through
Envoy. Only after namespace cleanup does it write matching passing k3d
verification beside the archive. The optional Compose test record does not
authorize deployment. A one-command manual pipeline tests rollback logic,
builds, runs this archive gate, and promotes to k3d in that order. It stops
before deployment if verification fails. K3d promotion validates the
passing record before loading images or importing them into the cluster. Only after Pod-image verification, k3d browser
E2E, and market k6 pass does promotion write a local result. See
[the pipeline guide](pipeline/README.md).

Jenkins has independent Core, BFF, Expedition, and frontend build jobs for an
uncommitted worktree and trusted `main`, plus one deploy-local job per service.
A service build tests its new image together with the other three currently
deployed k3d images in one checksummed archive. Both build modes are manual;
no Git polling or successful build automatically deploys. A shared Jenkins
lock serializes trusted-`main` builds. An explicit deploy job rejects a
changed baseline, promotes only the candidate image, verifies all four
running Pod digests, runs browser E2E and market k6, and rolls back the
target service if a post-rollout gate fails. The latest successful deployment
of a service wins; this local lab does not coordinate cross-service releases.

## API contract

Game Core exposes agency game state and persisted rune loadouts at
`/api/v1/agencies/{agencyId}`. The BFF forwards the same public paths, so the
frontend always calls `http://localhost:17080/api/...` rather than Core.

- `GET /api/v1/account` provisions and returns the Account corresponding to
  the authenticated Keycloak subject. `POST /api/v1/account/manager` creates
  its one Manager with a unique, case-insensitive display name. Manager
  creation transactionally provisions one personally owned Level 1 Warrior,
  Mage, and Archer (all skills Level 1) in a new Manager-owned Main Party. Re-reading the account does not
  duplicate them. The account response includes the Manager's personal gold,
  hero roster, item inventory, and rune inventory; these are separate from
  agency assets.
- `POST /api/v1/agencies` lets an onboarded Manager with no membership create
  a Level 1 agency with no agency-owned heroes or gold. Its 3-to-100-character name is unique
  case-insensitively, the creator becomes its `LEADER`, and `201 Created`
  returns its state with the Manager Main Party attached.
- `GET /api/v1/recruits` lists globally available initial NPCs for an
  onboarded Manager. `POST /api/v1/recruits/{recruitId}/claim` claims one for
  that Manager, without requiring agency membership, and returns the personal
  hero detail. `POST /api/v1/agencies/{agencyId}/recruits/{recruitId}/claim`
  explicitly claims a recruit for the agency and returns the updated agency
  state; only that agency's `LEADER` may call it. `GET /api/v1/agencies/{agencyId}/heroes/{heroId}`
  returns membership-protected detail for agency-owned heroes only.
- `GET /api/v1/agencies/{agencyId}/state` returns an agency, its leader and
  upgrade levels, agency heroes in `heroes`, the caller's personal heroes
  and agency-party participants in `personalHeroes`, parties and quests,
  rune and item inventory, hero rune slots, feed posts, and any persisted
  quest-combat snapshot. Party records expose `ownerManagerId`; hero records
  expose `ownerManagerId` only for personally owned heroes and expose
  `borrowingFeeGold` (default 0) for each hero.
- `PUT /api/v1/agencies/{agencyId}/heroes/{heroId}/rune-slots/{slotIndex}`
  equips the requested available rune in a slot and returns the updated agency
  state.
- `DELETE /api/v1/agencies/{agencyId}/heroes/{heroId}/rune-slots/{slotIndex}`
  unequips a rune and returns the updated agency state.
- `PUT /api/v1/agencies/{agencyId}/heroes/{heroId}/activity` changes an
  agency hero's activity to `TRAINING` or `RESTING` and returns the updated
  agency state.
- `PUT /api/v1/agencies/{agencyId}/heroes/{heroId}/borrowing-fee` accepts
  `{ "feeGold": 0 }` or another nonnegative integer. Only the agency leader
  can set the per-quest price of an agency-owned hero.
- `POST /api/v1/agencies/{agencyId}/parties` creates a named prepared party
  owned by the authenticated Manager and returns the updated agency state.
- `PUT /api/v1/agencies/{agencyId}/parties/{partyId}/heroes/{heroId}` adds
  one of that Manager's available personal heroes or an available hero owned
  by the same agency to their prepared party. Assignment itself is free;
  a hero already assigned to another party returns `409 Conflict`.
- `DELETE /api/v1/agencies/{agencyId}/parties/{partyId}/heroes/{heroId}`
  removes a hero from the authenticated Manager's prepared party.
- `PUT /api/v1/agencies/{agencyId}/quests/{questId}/start` starts an available
  quest with the authenticated Manager's `partyId` and nonnegative
  `expectedBorrowingFeeGold` in its request body. Core rechecks the sum of
  the party's agency-hero fees and, only for a valid start, atomically moves
  that amount from the party Manager's personal wallet to the agency treasury.
  Stale quotes and insufficient personal gold return `409 Conflict` without
  starting the quest or charging the Manager. These mutations return the
  updated agency state; another Manager's party appears as not found.
- `POST /api/v1/agencies/{agencyId}/quests/{questId}/combat/sync` advances an
  existing combat snapshot by elapsed wall time and returns the updated agency
  state.
- `POST /api/v1/agencies/{agencyId}/feed-posts` creates an agency-scoped text
  post and returns the updated agency state.
- `GET /api/v1/market/orders` returns the global open market order book.
- `POST /api/v1/market/orders` accepts `ownerType` (`MANAGER` or `AGENCY`),
  `side`, `itemId`, `quantity`, and `priceGoldPerItem`. `agencyId` is required
  only for an agency order and forbidden for a personal order. Core derives the
  personal Manager from the authenticated identity; an agency order requires
  leadership of the selected agency. The response includes `ownerType`,
  `ownerId`, `ownerName`, and the resulting order status. Personal and agency
  wallets and item inventories are reserved separately.
  In k3d, Envoy Gateway asks the BFF to validate the opaque browser session
  and CSRF token, then enforces a Redis-backed limit of five placement
  attempts per second per validated Keycloak subject across sessions,
  agencies, buy/sell sides, and gateway replicas. Excess attempts return
  `429` with `Retry-After: 1` and
  `X-Hero-Association-Rate-Limit-Layer: envoy`. Gateway Redis uses three
  Sentinel-managed Pods on separate k3d nodes. A primary failure may briefly
  produce fail-closed `500`s during election; a complete Redis outage keeps
  placement unavailable until recovery. Envoy never forwards those failures.
  The BFF does not apply a second market limit.
  Reads and cancellations are not limited.
- `DELETE /api/v1/market/orders/{orderId}` cancels an open order only for
  its personal Manager owner or a leader of its agency owner. It releases the
  remaining reservation to that same owner's wallet or inventory and returns
  the cancelled order. Clients refresh account and agency state separately
  after mutations. Market orders still execute inside Game Core's database
  transaction; the future Market service split is not yet implemented.
  Core has a separate, private Assets service contract for UUIDv7-keyed
  Manager/agency gold and item reservations, partial releases, and atomic
  trade settlement with idempotent operation receipts. It is not yet wired
  to the public Market path or exposed as a private Market-to-Core API.
  See [Assets contract](ASSETS_CONTRACT.md).
- `POST /api/v1/gold-transfers` moves existing gold between wallets in one
  Core transaction. To deposit, send
  `{ "direction": "MANAGER_TO_AGENCY", "agencyName": "Dawnwatch Agency", "amountGold": 10 }`.
  The sender is always the authenticated Manager; any Manager may deposit
  into any existing agency without membership. To withdraw, the agency
  leader sends
  `{ "direction": "AGENCY_TO_MANAGER", "agencyName": "Dawnwatch Agency", "managerName": "User 2", "amountGold": 10 }`.
  The recipient may be any existing Manager, including the leader. Names are
  matched case-insensitively after trimming whitespace. The positive whole
  amount moves exactly: no market fee, agency share, payment, or reward is
  applied. The response contains both wallet owners and their new balances.
  Invalid input returns `400`, unauthorized withdrawal `403`, an unknown
  agency or recipient `404`, and insufficient gold or an overflowing
  destination wallet `409`. Requests are not yet idempotent; clients must
  not automatically retry an ambiguous response. Transfer receipts and
  request idempotency are required before cross-service wallet operations.
- An unknown agency returns `404 Not Found` with an error message.

The initial recruitment board contains Alden Steelward (Warrior), Seris
Dawnflame (Mage), and Tarin Windmark (Archer). Each is a globally unique, free
Level 1 NPC that can be claimed once, either personally by an onboarded
Manager (the default) or for an agency by its leader through an explicit
agency claim. Ownership is exclusive across both routes. A recruited hero
starts in `TRAINING` with full class health and mana and 100% stamina. A
missing candidate returns `404 Not Found`; a previously claimed candidate
returns `409 Conflict`. A non-leader agency claim returns `403 Forbidden`.

The response includes all five rune slots for every hero, including empty
slots. Hero class values define base health, mana, and per-second health and
mana recovery. Every five seconds, a background worker restores agency heroes'
health and mana from their elapsed time: `TRAINING` uses the base class rate
and `RESTING` uses twice that rate. The same worker recovers stamina at one
stamina minute per real minute in Training or the agency Rest Level rate while Resting.
Equipping and replacing runes atomically moves one rune between the agency
inventory and a hero slot. A rune that is unavailable in inventory or a hero
who is on a quest returns `409 Conflict`; a slot outside 0 through 4 returns
`400 Bad Request`.
Agency item inventory contains stackable materials. The seed contains Magic
Crystals and Iron Ingots; item equipment and quest drops are not implemented
yet.
Agency levels are Agency, Training, Rest, Size, Reputation, and Intelligence.
Rest represents the agency's recovery facilities; there is no Medical Level.
Its concrete upgrade effect is still to be defined. Quest heroes cannot change
their agency activity or rune loadout and return `409 Conflict`.
Prepared-party members remain at the agency and retain their `TRAINING` or
`RESTING` activity until a quest starts. Each party has a Manager owner.
A new Manager receives a Main Party containing their three personal starter
heroes immediately at onboarding. Its agency is initially unset; creating an
agency attaches the Party without changing hero ownership. Every seeded
Manager also has one Party: User 1 retains Broken Pass Party, while the others
have Main Party. There is no Party deletion operation, so Managers keep at
least one Party. Creating additional parties remains supported but is not
the primary flow for now.
The owner can assign their own available personal heroes or available heroes
owned by the same agency, and start a quest with that party. Agency-hero
ownership does not change. Each agency hero has a leader-configured,
nonnegative gold fee per quest (zero by default). The entire fee is due from
the party Manager, including the agency leader, to the agency treasury when
the quest starts; assignment is free. Party membership cannot change while
the party is on an in-progress quest, and a hero already assigned elsewhere
cannot be moved into the party; both return `409 Conflict`. Party names must
be unique per Manager within an agency. Other game actions are still being
specified.
Quest definitions include a description, creature objective, party-size range,
duration estimate, and gold reward. The Map is player-facing in the isolated
k3d lab when Expedition integration and a Map-enabled frontend are deployed;
ordinary local development keeps that path disabled by default.
The planned Map domain will define fields, dungeons, reusable layouts, and
possible encounters. Expedition will own one persistent Party per Manager and
its Map run; a Quest will be an optional objective, never a prerequisite for
entering a Map. The first Map path will accept personal heroes only, with
agency-hero borrowing deferred. A wipe will end battle but leave the Party
on the Map until the Manager explicitly requests return. After a win, the
server waits for a Continue command before another fight. The Map has an
opt-in auto-continue toggle, off by default, that sends this command after
a short pause only while the page is open. It never advances a wipe or a
requested return. This flow does not yet replace the current quest-based API. See [GAME.md](GAME.md) and
[SERVICE_EXTRACTION.md](SERVICE_EXTRACTION.md).
Starting a quest requires a prepared party whose member count is inside that
quest's range. It changes the quest to
`IN_PROGRESS`, links it to the party, and changes every party member to
`ON_QUEST`. It persists `startedAt` and `expectedCompletionAt`, calculated
from the quest duration. A quest that is not `AVAILABLE` returns `409
Conflict`; an ineligible party size returns `400 Bad Request`. In-progress
combat progresses through its persisted snapshot and the combat-sync command.
The internal combat engine is deterministic: callers advance a supplied combat
time and supply its random source. It resolves independent basic-attack
timers, hero health and mana recovery, mage spell cooldowns and mana costs,
critical hits, deaths, and battle completion. Quest start first applies
agency recovery to each Hero, then persists a battle snapshot from the
recovered resources. It pins Hero class, level and skill baselines, starting
stamina, attack and recovery values, basic-attack mana cost, spell
eligibility/cooldowns, effective critical bonuses, and equipped rune slot
details. The API exposes the active combat state, not all internal pinned
inputs. A newly started quest resolves its Creature definition by name through
an optional Redis read-through cache and copies its versioned stats into each
combatant snapshot; existing battles are not rebalanced. PostgreSQL remains
authoritative. Cache entries expire after 60 seconds, so a changed definition
may take up to a minute to appear in a new battle; Redis errors fall back to
PostgreSQL. The canonical Troll starts with 2,000 health, while Forest Wolf
keeps 120. Spell formulas are still compiled rules, not versioned data.
The snapshot retains its latest 100 server-generated combat
events, including actions,
recovery, mana costs, hits, criticals, and defeats. A combat-sync command
restores a snapshot into the engine, advances it by the time since its
previous sync, and persists the result and any new events atomically. A
background worker uses the same operation every five seconds for all active
snapshots. `GET /state` is read-only and does not advance combat. Each
synchronization also persists the current health and mana of heroes in the
encounter. It drains each living hero by elapsed active battle time, stopping
at the actual terminal event or that hero defeat. Valid Warrior and Archer
basic attacks earn Melee and Distance points; mana actually spent earns Magic
points using the class aptitude and below-15-hour stamina penalty. The full
new event stream is processed inside the combat transaction before retaining
only the latest 100 UI events. Each creature defeat awards its full base
XP separately to every living party hero using the stamina of each hero at
the kill time. Provisional creatures currently have 100 base XP. Shield
combat points, economic rewards, and death resolution remain planned.
Agency stamina recovery is implemented.

Core now persists cumulative hero XP, fractional Melee, Distance, Magic, and
Shield points, and up to 48 hours of millisecond-precise stamina. Hero and
skill levels derive from those totals, and class-specific maximum health and
mana derive from hero level. New heroes start at Level 1 with all four skills
at Level 1. The existing Hero API still returns percentage stamina for the
frontend; a time-based API is planned separately. Combat skill and
creature XP awards are active; agency practice is not.
The agency-state Hero response includes cumulative `experience`, so clients
can observe XP earned between level-ups.

Combat XP is calculated separately for each living party hero from the full
creature base XP at defeat, without a party-size or damage split. Individual
stamina can change the final award. Shield combat progress awaits a
block rule. Agency Training recovers one stamina minute per real minute; Resting recovers two at Rest Level 1, plus 10% of that baseline
per later level. Training skill progress will start at 2x and gain 5% of that
baseline per later Training Level. Above 40 hours adds 50 percentage points
to hero XP only; below 15 hours halves hero XP and skill progress. A battle
with no kill can still drain stamina and advance skills. Gold and item rewards
remain deferred until game domains are separated.

Both five-second Core jobs use separate transaction-scoped PostgreSQL
advisory locks, so a competing Pod skips that tick rather than repeating
combat progression or agency recovery. Each lock spans the same transaction
as its updates and is released when the transaction ends. The next tick uses
persisted timestamps to catch up. A k3d integration test scales an isolated
Core deployment from two to four to eight Pods against one temporary PostgreSQL
database while read-only SQL traffic runs. It verifies recovery, combat event
continuity, and single quest resolution through Pod restarts.

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
remaining reserved gold or items. Personal orders require the authenticated Manager; agency orders require
agency leadership. Orders cannot match another order of the same owner.
The 10% market fee applies to both owner types. Whether an agency also takes
part of a personal market-sale payout is deferred; no additional agency share
is charged yet. Order history and expanded item categories are deferred.

Agency names, like Manager display names, are unique case-insensitively. The
initial agency-creation flow is intentionally limited to a Manager with no
membership; leaving an agency, invitations, and ownership transfer remain
separate rules.

All persistent entity IDs and API resource IDs use RFC 9562 UUID version 7
(UUIDv7). PostgreSQL stores them in native `uuid` columns. Sequential numeric
IDs must not be added for entities or exposed through the API.

## Runtime

- PostgreSQL stores game state.
- Database tables use singular entity names, including `agency`, `manager`,
  `hero`, `party`, `quest`, `creature_definition`, `quest_combat`, `quest_combatant`,
  `quest_combatant_rune`, `quest_combat_event`, `quest_combat_hit`,
  `rune`, `agency_rune`, `item`,
  `agency_item`, `manager_item`, `manager_rune`, `hero_rune`,
  `feed_post`, and `market_order`. A hero is either recruitable,
  agency-owned, or Manager-owned; personal heroes cannot be recruited by
  an agency.
- The standalone Combat sandbox and Core Combat-fact inbox were retired.
  Core Quest combat and Expedition Map combat still use the same pure
  `hero-association-lib/combat-engine` library; only Expedition owns live
  Map fight timelines and returns aggregated progress to Core.
- Until Flyway is introduced, Core's development and test profiles drop and
  recreate the schema on startup, then load deterministic state from
  `import.sql`. Packaged Docker Compose explicitly keeps this disposable
  reset behavior. The k3d lab instead runs a single bootstrap Job only when
  its Core schema is absent or an explicit reset is requested; normal Core
  Pods validate the schema without modifying data. For a schema-changing
  archive, explicit reset-aware promotion verifies the full archive and E2E
  result, stops Core and its HPA, bootstraps only the k3d Core database using
  the exact archived Core image, restores Core and its HPA, then runs the normal
  Pod-image, browser, and k6 gates. Keycloak and Redis data remain untouched. Because a data reset is not reversible by restoring an
  older image, failures after reset do not automatically roll back images.
  The seed contains
  local Accounts and Managers for user1, user2, and manager1 through
  manager10, plus a Core-only Soren fixture. User3 remains unprovisioned for
  onboarding tests. Dawnwatch Agency has six members and six heroes;
  Ironridge Exchange has four members and open market orders; Silverkeep Guild
  has three members. The seed also contains three globally available recruits,
  Broken Pass Party and its in-progress quest and initial Troll combat snapshot,
  Lost Courier, rune inventory, Magic Crystals, Iron Ingots, equipped runes,
  and two feed posts. See [TEST_DATA.md](TEST_DATA.md) for all credentials and
  memberships. Seeded Managers normally have zero personal gold; User 2 has
  100,000 gold for local testing, Soren and Manager 2 have 25 gold, Manager 3
  has 20, and Manager 4 has 200 to exercise
  exact, insufficient, and ample borrowing payments. All have empty personal
  item and rune inventories except Manager 3's Magic Crystals and Manager 4's
  Iron Ingots, and a distinct personal starter
  Warrior, Mage, and Archer. These 39 heroes are not agency assets or global
  recruits. Manager-owned parties, personal hero quest participation, and personal
  recruitment and leader-only agency recruitment are implemented.
  Personal market orders are implemented; agency-change workflows remain
  planned in [GAME.md](GAME.md).
  This development-only workflow does not retain
  application data.
  The product is in an early stage, so local and pre-production schema and
  seed-data changes may be applied directly by resetting and recreating data;
  they do not require backwards compatibility before Flyway is introduced.
- The active local environment is the k3d cluster. Envoy Gateway serves
  `heroassociation.test` and `auth.heroassociation.test` in full k3d and
  hybrid mode; there is no separate daily Traefik gateway. Core and Keycloak
  PostgreSQL, BFF/Core/Expedition Redis, RabbitMQ, Keycloak, and observability
  stay in k3d. Core, BFF, Expedition, and frontend can independently use
  k3d Pods or private WSL hot-reload processes behind their stable Services.
  A selected service's k3d Pods stop before its host process starts, and its
  previous replica count, route, and HPA are restored on exit. The first
  Core-only and individual-service hybrid browser checks passed; Map,
  WebSocket, and market policy also passed. See
  [LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md).
- Core Quarkus dev mode validates the existing k3d PostgreSQL schema,
  disables SQL seed loading and bootstrap, and uses a distinct loopback
  port-forward. Only explicit reset/reseed commands discard disposable Core
  data. BFF and Expedition take Keycloak and service credentials from k3d
  Secrets at runtime, without committing them. The hybrid launcher disables
  Quarkus Dev Services for host-run backends; telemetry still goes to the
  shared k3d collector instead of starting another LGTM container.
- The active candidate and browser gate use a disposable k3d namespace.
  The old Compose/Traefik browser runner is retired. Backend candidate
  component tests use private k3d dependencies instead of local Dev Services
  or Testcontainers. Packaged standalone Compose JVM/native workflows remain
  optional, not the default local development route.
- The shared k3d Envoy Gateway has restricted test-only HTTPS listeners
  for `app.e2e.heroassociation.test` and `auth.e2e.heroassociation.test`.
  Only labeled E2E namespaces may attach Routes to them. The test leaf
  certificate uses the ignored local CA; hostnames resolve only inside test
  runners. A disposable full-stack runner creates independent databases,
  Redis, RabbitMQ, Keycloak, and application Pods. It runs ten browser
  journeys and k6 market thresholds with 30-second test tokens and a
  two-second BFF refresh skew. A saved session survives replacement of both
  disposable BFF replicas. An outage of only the disposable BFF Redis
  denies that session; fresh login succeeds after Redis recovery. Source
  mode uses current daily image refs; archive mode checks all running
  application Pod image IDs against the exact archive. Only after namespace
  cleanup does it write the required k3d verification record.

- The local Keycloak realm uses the versioned `hero-association` CSS-only login
  theme. It extends Keycloak's `keycloak.v2` theme and matches the frontend's
  dark, gold-accented visual language without replacing Keycloak templates. It
  provides two seeded Manager users plus `user3@mail.com`, initially
  unprovisioned for onboarding, no-agency, and first-agency creation tests.
  Native registration does not request first or last name: those optional
  Keycloak fields are administrator-managed seed data in this early stage.
- The BFF uses Keycloak's confidential authorization-code flow with PKCE. Its
  Keycloak token state is stored server-side in the BFF Redis instance;
  browser sessions use an `HttpOnly`, `SameSite` cookie. Production Redis must
  be private, authenticated, TLS protected, and encrypted at rest. `/api/v1/session` is
  public, but all proxied game routes require a BFF session and state-changing
  requests require the signed double-submit CSRF token. If a session cookie
  references missing or expired Redis token state, the BFF rejects that login
  as unauthenticated rather than returning a server error. A Redis connection
  failure still fails closed and is not treated as an ordinary expired login.
- Signed-out frontend users can select **Sign in** or **Create account**. The
  latter starts Keycloak's native registration page through the protected BFF
  OIDC route, with Quarkus forwarding only the standard `prompt=create` hint
  while retaining state and PKCE ownership.
- Signing out uses OIDC RP-initiated logout. The BFF clears its local session,
  Keycloak ends the browser SSO session, and the browser returns through the
  registered, state-validated BFF post-logout callback before it is redirected
  to the frontend. Browser cookies are not cleared globally during this
  redirect because the short-lived post-logout state cookie is needed to
  validate the callback. The local Keycloak realm registers distinct callbacks
  for the normal development BFF and the isolated Playwright E2E frontend
  proxy only.
  An already signed-out visit to `/auth/logout` returns to the frontend
  without trying provider logout.
- Game Core requires a valid Keycloak bearer access token for every `/api/...`
  route. The BFF's Keycloak client mapper adds the `hero-association-core`
  audience to its access tokens before the BFF forwards them over the private
  service network.
- The first authenticated Account request provisions a UUIDv7 `account` row
  from the immutable Keycloak subject. React then requires the player to choose
  a unique Manager name. A Manager without an agency membership can create one
  Level 1 agency with no agency-owned assets and becomes its leader; `agency_member` controls access
  to agency state and commands. Both roles can run gameplay commands, while
  leaders alone can create or cancel agency-owned market orders. Any Manager
  can create or cancel their own personal orders.
- The local Keycloak realm seeds `user1@mail.com` / `user1` and
  `user2@mail.com` / `user2` with matching Account and Manager records. Their
  direct Keycloak profile names are `User1 Last1` and `User2 Last2`; their
  seeded Manager names are `User 1` and `User 2`, so local sessions and game
  data are immediately distinguishable. They must never be used outside local
  development.
- Standalone Docker Compose runs Core and Keycloak PostgreSQL, separate Core
  cache and BFF session Redis, Game Core, and BFF using JVM packages by
  default. Core remains on the private Compose network; BFF and Keycloak
  publish development HTTP ports `17080` and `17180`. It has no public HTTPS
  gateway. Integrated browser development uses the k3d Envoy Gateway.
- Each service has an independent Maven fast-jar build. Native compilation is
  currently an opt-in Game Core build with `-Dnative`.
- Game Core's `Dockerfile.native` provides a `native-runtime` target that
  packages a prebuilt Linux native executable and a `native-multistage` target
  that compiles it in Docker. The native Docker Compose workflow runs that
  native Core behind the JVM BFF, Redis, and PostgreSQL.

## Frontend prototype

- The independently runnable React frontend lives in `frontend`.
- The frontend starts by requesting the BFF session. Signed-out users see a
  sign-in screen; signed-in users receive no Keycloak tokens in browser storage.
  The Vite development proxy forwards both `/api` and `/auth` routes to the
  BFF so the OIDC redirect uses the BFF's `localhost:17080` callback. Its proxy
  origin is normally rewritten to the BFF target; the isolated container-browser
  E2E stack preserves the browser-visible host so its callback returns through
  Vite. That stack explicitly allowlists its Docker browser host in Vite;
  normal development keeps Vite's default host protection.
- After Account and Manager onboarding, it opens the first authorized agency
  membership through Vite's `/api` development proxy. If the API is unavailable,
  a visible notice explains that the UI has fallen back to a local fixture.
- The frontend needs the BFF on `http://localhost:17080` by default;
  `VITE_API_PROXY_TARGET` can override that Vite development proxy target. A
  local `VITE_API_PROXY_CHANGE_ORIGIN=false` setting preserves the original
  host for controlled OIDC callback workflows. A production deployment routes
  same-origin `/api` requests to the BFF; the browser never calls Game Core
  directly.
- The frontend refreshes agency state and the recruitment board every five
  seconds while its browser tab is visible, and immediately when the tab becomes
  visible again. This keeps agency recovery, feed posts, market orders, and
  background quest progress current without requiring WebSocket or server-sent
  event connections. This remains the normal Quest behavior. An opt-in browser-to-BFF
  WebSocket now carries Expedition fight snapshots from the shared combat-engine
  path; Start, Continue, and Return remain CSRF-protected HTTP commands. There
  is no temporary Core WebSocket or SSE. See the
  [Combat and Expedition plan](COMBAT_EXPEDITION_PLAN.md) and the
  [planned run contract](EXPEDITION_CONTRACT.md). The contract reserves one
  active Map run per Manager with a versioned, durable Redis snapshot; the
  normal local-development frontend Map is still hidden. The private
  `hero-association-expedition` module now keeps one active run per Manager in
  dedicated Redis, calculates a pinned fight once with the shared engine,
  and atomically commits only terminal Hero state. It waits for explicit
  Continue after a win, parks a wipe, and defers Return until a current fight
  ends. The private Return handoff now freezes one aggregate in Redis,
  publishes it through RabbitMQ, and retains it until Core applies Hero and
  Manager Assets once and sends a matching acknowledgment. Core now exposes a service-key-protected internal admission API: it resolves
  the authenticated Manager, reserves a recovered Hero baseline including rune
  effects, and can release a proven-absent orphan. Expedition has the server-side
  Core client, entry coordinator, and atomic Redis cancellation fence; its
  periodic orphan scanner is disabled by default. Expedition now validates a
  dedicated Keycloak audience for owner-scoped Start, Get, Continue, Return,
  and active-run HTTP commands. BFF routes only that API prefix to Expedition;
  the Expedition player API remains disabled by default. A disabled-by-default BFF WebSocket
  handshake checks session, exact Origin, and run ownership before sending
  one current snapshot on connect or reconnect. Its scheduler pushes changed
  Redis-backed fight visuals to locally subscribed sockets through a private
  Expedition API without per-frame Core reads. The frontend has a feature-flagged Map page
  for the first Troll Field, active-run recovery, live visuals, and explicit
  Continue/Return. It is enabled in the isolated k3d frontend build; both settlement consumers
  are enabled by k3d integration. Core Quest combat remains available for
  players. The disposable k3d Playwright gate verifies Map entry, live and
  reconnected WebSocket visuals, explicit Continue, deferred Return with no
  viewer, and Core settlement without changing daily game data. See
  [Expedition settlement](EXPEDITION_SETTLEMENT.md).
  Broker setup creates settlement and acknowledgment topology before either
  service starts. Core and Expedition use separate accounts with no configure
  permission: each publishes only to its own exchange and reads only its queue.
  Private k3d Redis, RabbitMQ, and Expedition are staged. An explicit
  integration command enables Core admission/settlement, Expedition APIs, and
  the BFF WebSocket; an authenticated k3d journey passed, with the frontend Map now enabled.
  The four-image local pipeline now promotes Core, BFF, Expedition, and
  Map-enabled frontend together; the Expedition-only lane verifies the other
  three running images before promotion. Both paths passed archive-backed,
  authenticated k3d Map, Expedition, and market gates.
- The Map screen is the player-facing battle view in k3d. It reuses the
  Phaser battle renderer for the party and three Trolls, including health,
  mana, equipped rune slots, Mage spell slots, timed hits, critical effects,
  and recovery animations. The first seeded Troll has 2,000 HP and 4 attack
  damage so its three-creature encounter remains playable. Spell slots remain visible but locked below their
  required Magic Level. An accessible panel near the battle names equipped
  runes and their effects and shows Mage spell requirements, mana cost, and
  current cooldown or readiness. On narrow screens the three-Troll field
  scrolls horizontally rather than overlapping the combatants. The Map also
  shows current Hero XP, skill points, stamina, and carried assets from the
  authoritative run; Hero totals include progress from before entry, and
  changes are permanently saved only on return. The first Troll Field does
  not yet award carried loot. User 2's seeded personal party has equipped runes
  and a Magic Level 15 Mage for this battle demonstration. Expedition
  calculates the current non-interactive fight once and stores its
  event windows in Redis; visual reads do not rerun the engine. The BFF
  WebSocket sends only the current state and recent ordered events. Reconnect
  loads current state without replaying old hits. When the Map is reopened or
  the tab resumes after a gap, the browser discards pending visual effects and
  paints the current server frame immediately; only subsequent live events are
  animated. Continue and Return remain server commands; the optional Map
  auto-continue control sends Continue 1.5 seconds after a victory while the
  page is open. Simultaneous Continue and Return submissions are guarded in
  the browser, while the server enforces idempotency and state versions. The
  browser never decides attacks or outcomes. See
  [the fight timeline](COMBAT_TIMELINE.md).
- The Quests screen shows available objectives and active or resolved progress.
  It no longer embeds the legacy Phaser scene or polls the combat-sync API.
  Existing Core quest combat can continue server-side, but Map encounters do
  not advance Quest objectives yet; that integration is deferred.
- The Heroes screen shows the signed-in Manager's personal starter roster
  and gold separately from the agency roster. The Manager can create a party,
  assign or remove their personal heroes or available agency heroes, and send
  an eligible prepared party on a quest. The agency leader can set a per-hero
  borrowing fee. The
  screen also loads the global recruitment board and lets an onboarded Manager
  claim a free initial NPC for their personal roster by default, or explicitly
  for their agency if they are its leader. The roster refreshes immediately;
  a claimed NPC disappears from the board for everyone. The screen also
  separates active quest parties, prepared parties, and unassigned heroes at
  the agency. Agency heroes can persistently switch between Training and
  Resting, without numeric training stats. Prepared parties can be named,
  filled, and changed through the backend; their members retain their agency
  activity until a quest starts.
- The Quests screen reads available, active, and resolved quests from the API.
  A Manager can select their own prepared party and see its total agency-hero
  borrowing fee alongside their personal gold. A quest start submits that exact
  quote, then refreshes agency and account balances; a stale quote is rejected
  and the UI refreshes both. Every quest start creates a server combat snapshot using each party member's
  current resources, class combat values, and equipped Critical Chance and
  Critical Damage Rune effects, plus one creature for every required objective.
  Until per-creature difficulty is designed, new creatures use a shared
  provisional profile: 120 health, 10 damage, a 1.6-second attack
  interval, 100 mana, no recovery, and no critical chance. The planned
  Creature catalog will start with one Troll definition at 2,000 health;
  other stat values remain provisional and data-driven. Seeded Trolls
  already have 2,000 health, while newly started quests still use 120.
  Phaser renders any active quest snapshot. `HERO_VICTORY` completes a quest and
  `CREATURE_VICTORY` fails it. Either result sets `finishedAt`, releases the
  party, and returns its heroes to Training. Economic rewards and the planned
  non-permanent defeat penalty remain unimplemented.
- A background worker restores agency hero health and mana every five seconds
  from elapsed full seconds. Training uses the base class rate and Resting uses
  twice that rate. The worker also recovers agency stamina at the documented rates.
- The server combat engine recovers hero health and mana once per second. Warrior
  recovery is 10 health and 2 mana; Mage recovery is 2 health and 10 mana;
  Archer recovery is 6 health and 6 mana. The UI still shows prototype XP;
  Core now awards hero XP on creature defeat.
- Stamina is stored as time up to 48 hours, but the existing Hero API still
  exposes a derived percentage. The display keeps prototype colors: green
  at 80% or more, yellow from 30% through 79%, and red below 30%. The
  planned UI compares raw time instead: green above 40 hours, yellow from
  15 through 40 hours, and red below 15 hours. At the current `1x` rate, hero XP is
  150%, 100%, and 50%; a planned `2x` rate would yield 250%, 200%, and 100%.
- Core now stores fractional Melee, Distance, Magic, and Shield points for
  every hero and derives their skill levels, starting each skill at Level 1.
  Valid Warrior and Archer basic attacks now earn Melee and Distance points;
  mana actually spent earns Magic points. Successful shield blocks and agency
  practice are not implemented yet. Planned agency practice earns 2x points
  before class and server rates; see `PROGRESSION.md` for provisional rates.
  A Mage basic attack spends 20 mana when available and earns one base Magic
  point before class and stamina rates. Below 20 mana, the attack still deals
  its normal damage for free but earns no Magic points. Other basic attacks
  remain free.
- Every hero card ends with five rune slots loaded from the API. Heroes with
  learned spells also show their spell slots; Elara has both mage spells,
  while new Mages start at Magic Level 1 and unlock Fire Ball at Magic Level 10.
  Critical Chance and Critical Damage Runes affect server combat;
  other rune stat effects do not yet change gameplay. Combat displays five
  read-only rune slots for each hero so their equipped loadout is visible;
  creatures do not display rune slots.
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

## Isolated k3d backend lab

The active local environment uses k3d Envoy Gateway in full-cluster and
hybrid hot-reload modes. Its backend runs JVM BFF and Core images
with private ClusterIP Services and opt-in Istio sidecars. Separate PostgreSQL
databases serve Core and Keycloak, and a
separate Redis instance stores BFF sessions. Generated lab-only credentials
and a local Keycloak realm register callbacks and issuer URLs on standard
HTTPS port 443. Standalone Compose JVM/native runs use separate development
ports and do not provide a second public gateway.
Kubernetes startup, readiness, and liveness probes use Quarkus SmallRye Health.
BFF and Core use rolling updates and separate CPU-based `autoscaling/v2` HPAs with
two to eight replicas. The HPAs use Pod CPU, including Istio sidecar overhead,
and hold downscale recommendations for 60 seconds in this disposable lab.
Each Pod waits 15 seconds before shutdown to drain old connections; an
explicit Core database reset removes the Core HPA before stopping Core Pods.
The k3d frontend is a separate non-meshed Nginx Pod with a private Service;
Envoy Gateway routes
its root path to the frontend while `/api` and `/auth` reach BFF. The normal
Hybrid Vite and Quarkus dev processes reuse these same Envoy routes. The
daily seeded-user smoke suite checks authentication and agency access; the
full disposable k3d suite additionally covers registration, market,
Expedition, sessions, and outages without resetting daily data. Windows
browser certificate trust remains a one-time setup step.

Core's k3d `PeerAuthentication` is STRICT and workload-scoped; BFF-to-Core
traffic is reported by Istio as `mutual_tls`, while a plaintext request from
the non-meshed frontend Pod is rejected. BFF and Core have distinct Kubernetes
ServiceAccounts. A Core-scoped Istio `AuthorizationPolicy` allows only callers
presenting the BFF ServiceAccount identity; another meshed identity receives
403. BFF remains reachable through Envoy Gateway. Core's OIDC checks still
authorize the end user on protected APIs. Changing Core's ServiceAccount
restarts its Pod without resetting the Core database.

A preliminary k3d baseline uses four authenticated clients for 60 seconds
of read-only agency-state requests through Envoy Gateway, BFF, and Core,
sharing one local-only account session.
Resource requests are 300m CPU / 256Mi memory for BFF and 500m / 384Mi for
Core, based on observed single-Pod CPU and memory under that traffic. This
does not establish capacity for writes or combat. A separate 16-client
autoscaling run exercises authenticated reads and a BFF rolling restart;
scaling on one computer does not demonstrate multi-node or multi-AZ resilience.
A separate k3d capacity lab creates temporary BFF/Core Deployments and a
temporary Core database. Header-marked requests exercise authenticated
agency-state reads and hero-activity writes through the temporary stack;
ordinary requests continue to use the live BFF. The test only establishes
a workload-specific latency boundary on this one computer.

In the k3d lab, BFF and Core export OTLP traces, HTTP/JVM metrics, and
structured logs to the private observability Pod. Packaged containers
write JSON console logs. BFF creates a client span for its Java HTTP call
and propagates W3C trace context to Core, producing a single distributed
trace. Auth routes and raw agency-ID paths are excluded from tracing, and
Redis client spans are disabled to avoid exporting connection strings.
No bearer tokens, request bodies, email addresses, or manager names are
added as custom telemetry attributes. Normal host-run development keeps
telemetry disabled unless explicitly opted in; the same is true of normal
Docker Compose. In k3d, a per-node collector exports selected Istio and
Envoy Gateway metrics, Pod CPU/memory metrics for the application and Gateway
namespaces, Istio diagnostic logs, and sanitized Envoy proxy access logs.
Raw request paths and client IPs are removed from collected access logs;
diagnostic messages can still contain arbitrary text. The lab provisions
traffic and scaling dashboards, retains Prometheus, Loki, and
Tempo data for up to 24 hours, and uses ephemeral storage that is cleared
when the observability Pod is replaced.

A build-once local delivery lane packages tested Core, BFF, and Expedition
JVM images plus the frontend image in a checksummed archive. The disposable
k3d archive gate records a matching passing result before promotion is
allowed. Normal promotion imports those exact images, updates the four k3d
application Deployments, waits for rollouts, and runs k3d browser tests. A
failed normal rollout or test restores prior image references. An explicit
`--reset-core-db` promotion requires a complete verified archive, resets
only the k3d Core database using that archive's Core image, then runs the same
gates; it cannot safely roll back to an older Core image after resetting data.
Neither mode changes the normal development stack.
