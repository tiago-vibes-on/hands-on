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
architecture direction; Market and Assets now have independent services and databases.

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
    heroes, parties, and feed posts; asset values are immutable projections
    from Assets; Expedition keeps pinned combat inputs; it consumes the pure, non-persistent
    `hero-association-lib/combat-engine` rules engine
  - `application`: game-state use cases
  - `application.exception`: application exceptions
  - `api.v1.agency`: HTTP resource and response models for agency state
  - `api.v1.error`: HTTP error responses and exception mapping
  - `repository`: Panache repositories for game-state reads

- `backend/hero-association-market`: order placement, matching, trade and recovery
  records on port `8084`, with its own PostgreSQL database.
- `backend/hero-association-assets`: wallets, loose inventory, catalogs, equipped
  slots, reservations, immutable receipts and available-resource postings on port
  `8085`, with its own PostgreSQL database. Core retains permissions, eligibility
  and durable orchestration; no service reads another service's database.
  See [the extraction audit and contracts](ASSETS_ARCHITECTURE.md).

- `backend/hero-association-world`: immutable Creature and Map catalogs on port
  `8086`, with a separate PostgreSQL database. Expedition admission pins a
  complete versioned plan; active runs do not read changing catalog values.
- `backend/hero-association-quest`: optional Manager objectives and return
  reward recovery on port `8087`, with a separate PostgreSQL database. The
  Expedition path replaces Core's old Quest-driven battle lifecycle. See
  [the World and Quest contracts](WORLD_QUEST_ARCHITECTURE.md).

`AgencyStateService` coordinates the initial state query. An unknown agency is
translated to `404 Not Found` at the Core API boundary. The BFF preserves that
status and response body for the browser.

## Local build validation

The local pipeline tests Core, BFF, Expedition, Market, Assets, World, and Quest against disposable k3d
PostgreSQL, Redis, and RabbitMQ before packaging their JVM images with
`-DskipTests`; frontend lint and build stay local. The images are saved in one
checksummed archive. Candidate builds use disposable dependencies; promotion requires all eight daily application
services in full k3d mode and never promote them automatically. Direct Maven
builds remain independent and may use local Dev Services or Testcontainers.
Core tests do not retain PostgreSQL Dev Services across runs, even when
Testcontainers reuse is enabled globally on the developer machine.
Before deployment, the disposable k3d E2E gate must run those exact images,
verify every application Pod image ID against the archive, and pass browser,
session, pinned World-outage dungeon, Quest reward recovery, BFF outage, and market k6 checks through
Envoy. Only after namespace cleanup does it write matching passing k3d
verification beside the archive. The optional Compose test record does not
authorize deployment. A one-command manual pipeline tests rollback logic,
builds, runs this archive gate, and promotes to k3d in that order. It stops
before deployment if verification fails. K3d promotion validates the
passing record before loading images or importing them into the cluster. Only after Pod-image verification, k3d browser
E2E, and market k6 pass does promotion write a local result. See
[the pipeline guide](pipeline/README.md).

Jenkins has independent Core, BFF, Expedition, Market, Assets, World, Quest, and frontend build jobs for an
uncommitted worktree and trusted `main`, plus one deploy-local job per service.
A service build tests its new image together with the other seven currently
deployed k3d images in one checksummed archive. Both build modes are manual;
no Git polling or successful build automatically deploys. A shared Jenkins
lock serializes trusted-`main` builds. An explicit deploy job rejects a
changed baseline, promotes only the candidate image, verifies all eight
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
  and agency-party participants in `personalHeroes`, parties,
  agency rune and item inventory, the caller's `personalRuneInventory`, hero
  rune slots and feed posts. Party records
  expose `ownerManagerId`; Hero records expose `ownerManagerId` only for
  personally owned heroes and `borrowingFeeGold` (default 0) for each hero.
- `PUT /api/v1/agencies/{agencyId}/heroes/{heroId}/rune-slots/{slotIndex}`
  takes `{ "operationKey": "UUIDv7", "runeId": "...", "sourceOwnerType": "MANAGER" | "AGENCY" }`,
  equips the available rune from that inventory, and returns updated agency
  state. Any agency member can use agency runes for an agency Hero or one of
  their own personal Heroes while the Hero is at the agency.
- `DELETE /api/v1/agencies/{agencyId}/heroes/{heroId}/rune-slots/{slotIndex}`
  requires `X-Operation-Key: UUIDv7`, unequips a rune into the acting Manager's inventory and returns updated
  agency state.
- `PUT /api/v1/agencies/{agencyId}/heroes/{heroId}/activity` changes an
  agency hero's activity to `TRAINING` or `RESTING` and returns the updated
  agency state.
- `PUT /api/v1/agencies/{agencyId}/heroes/{heroId}/borrowing-fee` accepts
  `{ "feeGold": 0 }` or another nonnegative integer. Only the agency leader
  can configure a future borrowing price; the current personal-hero Map path charges no fee.
- `POST /api/v1/agencies/{agencyId}/parties` creates a named prepared party
  owned by the authenticated Manager and returns the updated agency state.
- `PUT /api/v1/agencies/{agencyId}/parties/{partyId}/heroes/{heroId}` adds
  one of that Manager's available personal heroes or an available hero owned
  by the same agency to their prepared party. Assignment itself is free;
  a hero already assigned to another party returns `409 Conflict`.
- `DELETE /api/v1/agencies/{agencyId}/parties/{partyId}/heroes/{heroId}`
  removes a hero from the authenticated Manager's prepared party.
- `GET /api/v1/maps` and `GET /api/v1/creatures` read the latest versioned World catalogs.
- `GET /api/v1/quests` reads optional definitions, the Manager's active assignment,
  recent completed/cancelled assignments, and whether agency commands are allowed.
- `POST /api/v1/quests/{definitionId}/accept` accepts one optional objective;
  `POST /api/v1/quests/assignments/{assignmentId}/cancel` cancels it. Both require
  a UUIDv7 `commandId`, bind it to the authenticated Manager and exact request,
  and allow exact replay. They require being at the agency with no active run.
  Quests select no Party and charge no fee. Progress is banked on return;
  fulfilled objectives credit gold/items/runes through Assets once per assignment.
- Expedition admission accepts any published Map UUIDv7, pins its exact version,
  Creature definitions, Hero baseline and optional Quest assignment. Fields repeat;
  dungeons finish after their last encounter. `DUNGEON_COMPLETED` permits Return
  and rejects Continue. A wipe also waits for explicit Return.
- `POST /api/v1/agencies/{agencyId}/feed-posts` creates an agency-scoped text
  post and returns the updated agency state.
- `GET /api/v1/market/orders` returns the global open market order book.
- `POST /api/v1/market/orders` accepts a caller-generated UUIDv7 `placementId`, `ownerType` (`MANAGER` or `AGENCY`),
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
  after mutations. BFF routes `/api/v1/market/**` to the independently runnable
  Market service and its own PostgreSQL database and account. Assets owns wallets, inventories and catalog definitions. Core retains
  player identity and agency permission checks.
  The old Core Market routes and order writer have been removed.
  A confirmed placement returns `201` with the order. An uncertain reservation
  returns `202` with `{ id, status, orderId, message }` for the placement;
  `GET /api/v1/market/placements/{placementId}` is restricted to its original
  JWT subject. Exact retries reuse the ID and immutable payload; reuse by
  another subject or with another payload returns `409`.
  `GET /api/v1/market/orders/{orderId}` checks current owner permission through
  Core. Cancellation returns `202` while pending trades or closure remain
  unresolved, and `200` only after confirmed closure. Pending cancellation
  freezes new matching; its allocated trades still settle. Responses include
  `quantityPending`; available quantity is `quantityRemaining - quantityPending`.
  Market records placement, trade, and cancellation work before delivering
  authenticated private commands. Assets' `/internal/v1/assets/**` requires a
  dedicated Market service credential; context and new reservations also
  require the player's Assets-audience token. Assets obtains Manager identity or agency
  leadership from Core using the original token, then commits its mutation locally. Market validates its
  own token audience and forwards the original token only during player calls;
  no JWT is persisted. Workers use receipts, 60-second claims and stable keys
  to recover delivery after failures and restart. An unconfirmed reservation
  is permanently closed before the placement is abandoned. Assets applies the
  existing seller fee and buyer price-improvement refund in one transaction.
  See [Assets contract](ASSETS_CONTRACT.md), [recovery](ASSETS_RECOVERY.md), and
  [Market architecture](MARKET_ARCHITECTURE.md). Public trade history is future work.
- `POST /api/v1/gold-transfers` moves existing gold between wallets in one
  Assets transaction. BFF routes this endpoint directly to Assets. To deposit, send
  `{ "operationKey": "<UUIDv7>", "direction": "MANAGER_TO_AGENCY", "agencyName": "Dawnwatch Agency", "amountGold": 10 }`.
  The sender is always the authenticated Manager; any Manager may deposit
  into any existing agency without membership. To withdraw, the agency
  leader sends
  `{ "operationKey": "<UUIDv7>", "direction": "AGENCY_TO_MANAGER", "agencyName": "Dawnwatch Agency", "managerName": "User 2", "amountGold": 10 }`.
  The recipient may be any existing Manager, including the leader. Names are
  matched case-insensitively after trimming whitespace. The positive whole
  amount moves exactly: no market fee, agency share, payment, or reward is
  applied. The response contains both wallet owners and their new balances.
  Invalid input returns `400`, unauthorized withdrawal `403`, an unknown
  agency or recipient `404`, and insufficient gold or an overflowing
  destination wallet `409`. A caller-generated RFC UUIDv7 `operationKey`
  is required; missing or invalid keys return `400`. Assets commits an
  `asset_command_receipt` with both wallet changes. Exact retries by the same
  Manager return the original response and balance snapshot without moving
  gold again; changed inputs or requester return `409`. Normalize names by
  trimming and matching case-insensitively. The frontend retains uncertain
  keys in session storage, scoped to Manager and inputs, across retries and
  same-tab reloads. It clears a key after confirmation and state refresh or
  definitive rejection, while network failures, authentication expiry, and
  server errors retain it. Refresh account/agency state for current balances.
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
Manager and agency rune inventories contain only unequipped runes. A Hero owns
only the runes in its five equipped slots, not gold or a separate inventory.
Equipping transfers one rune atomically from the chosen Manager or agency
inventory to a Hero slot. Replacing or unequipping transfers the old rune to
the acting Manager's inventory by default. Any agency member can use that
agency's runes; this does not change leader-only agency gold and Market rules.
The Hero must be at the agency, not on an Expedition. An unavailable
rune or away Hero returns `409 Conflict`; a slot outside 0 through 4 returns
`400 Bad Request`.
Agency item inventory contains stackable materials. The seed contains Magic
Crystals and Iron Ingots; item equipment remains planned. Creature drop evaluation is implemented, but
initial Creature definitions intentionally have empty economic drops. Quest rewards
introduce explicit gold and item payouts.
Agency levels are Agency, Training, Rest, Size, Reputation, and Intelligence.
Rest represents the agency's recovery facilities; there is no Medical Level.
Its concrete upgrade effect is still to be defined. Away heroes cannot change
their agency activity or rune loadout and return `409 Conflict`.
Prepared-party members remain at the agency and retain their `TRAINING` or
`RESTING` activity until an Expedition starts. Each party has a Manager owner.
A new Manager receives a Main Party containing their three personal starter
heroes immediately at onboarding. Its agency is initially unset; creating an
agency attaches the Party without changing hero ownership. Every seeded
Manager also has one Party: User 1 retains Broken Pass Party, while the others
have Main Party. There is no Party deletion operation, so Managers keep at
least one Party. Creating additional parties remains supported but is not
the primary flow for now.
The owner can assign their own available personal heroes or available agency
heroes to a Party. The current Map path admits one to four personal heroes only;
agency borrowing is deferred and no fee is charged. Active Expedition membership
is immutable, including adding an idle Hero to an away Party. Each Party name
is unique per Manager within an agency.

World owns append-only versioned Creature and Map definitions in separate
PostgreSQL. Maps reference exact Creature versions, grouped into named encounters
and ordered floors with a layout identifier. Core durably pins the complete plan
before reserving Heroes; an exact retry reuses the original plan. Expedition
copies it to Redis and performs no World lookup during combat or Continue.
Troll Field repeats three Trolls (2,000 HP, 4 attack, 100 base XP). Broken Pass
Cavern has three Forest Wolves on floor one and a Troll boss on floor two.
Creature gold and probabilistic item/rune drops use a separate deterministic RNG
stream, so adding drops cannot change combat rolls. Results, kill counts, Hero
progression and Quest progress commit in the same Redis fight transition.

Quest owns immutable definitions, assignments, admission pins, return receipts
and payout recovery. A Manager may accept one optional Quest at the agency,
complete it through one or more Expeditions, or cancel at the agency. Supported
objectives are Creature kill counts, boss defeats and dungeon completions;
objectives can restrict eligible Maps. Progress cannot exceed the requirement.
The admitted assignment version and reward remain fixed throughout the run.
Completion and cancellation permit later acceptance with a new assignment ID.

A win waits for Continue; a wipe or completed dungeon waits for Return.
Return requested during a fight takes effect when that fight finishes. The
frontend's optional auto-continue acts only while the Map page is open and the
run is waiting after a victory. Core's old SQL Quest battle API, tables, Creature
cache, combat-sync command and five-second combat worker are retired.

Rune commands remain durable Core asset workflows. Supply UUIDv7 `operationKey`
for equip and `X-Operation-Key` for unequip. Core stages the authorized request
and Hero fence, calls Assets outside the SQL transaction, and confirms the exact
receipt before clearing the fence. Unknown delivery returns `202`; exact replay
or `GET /api/v1/asset-operations/{operationKey}` recovers the original operation.
Conflicting receipts retain the fence. The browser stores stable command keys
through uncertain delivery and reloads; it does not optimistically move runes.

The deterministic combat engine resolves attack timers, health/mana recovery,
spells, criticals, deaths and terminal outcomes from pinned inputs. Expedition
stores one fight timeline, bounded visual event windows and terminal state in
Redis; it writes no per-hit SQL or broker messages. XP and skills advance for
eligible living Heroes with their stamina at the event time. Spells remain
compiled rules rather than versioned catalog data. Shield progress awaits a
block rule.

Return settlement uses schema version 2 and includes pinned Map identity/version
and nullable Quest progress. Its canonical bytes sort unordered Quest Map IDs
and resource-map keys while preserving skill decimal precision, so a restart
retains the same settlement digest. Core validates the entire aggregate before asking
Quest to bank progress and confirm any payout. Assets commits a Quest reward and
receipt atomically under the assignment UUID. Quest then marks the assignment
completed. Core separately confirms carried Assets credit and Hero progression
through its durable cursor. Rabbit acknowledgement and Redis removal follow all
confirmations. A dependency outage retains the away fences; workers recover the
same immutable requests after restart. Capacity overflow remains pending until
space is freed. See [World and Quest contracts](WORLD_QUEST_ARCHITECTURE.md),
[Assets contracts](ASSETS_CONTRACT.md) and [recovery](ASSETS_RECOVERY.md).

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
with no kill can still drain stamina and advance skills. Quest gold and item rewards are credited by Assets on fulfilled return;
Creature seed drop amounts remain an economic-content decision.

Core's five-second agency recovery job uses a transaction-scoped PostgreSQL
advisory lock, so competing replicas skip a tick. Expedition fight transitions
use atomic Redis state/version checks; Quest and Assets serialize commands and
payouts under their own database locks and exact receipts.

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
- Database tables use singular entity names. Core owns identity, agency, Hero,
  Party, reservation and orchestration tables; World owns `creature_definition`
  and `map_definition`; Quest owns `quest_definition`, `quest_assignment`,
  `quest_manager`, `quest_command` and `quest_admission`. Assets and Market retain
  separate databases and roles. Core contains no Creature or Quest battle tables.
- Shared `combat-engine` and `game-contracts` libraries contain no persistence or
  service runtime. Core has no Redis dependency; BFF sessions and Expedition runs
  use separate Redis instances. RabbitMQ carries immutable return aggregates and
  owner acknowledgements.
- Until Flyway is introduced, Core, Assets, Market, World and Quest development and test profiles drop and
  recreate the schema on startup, then load deterministic state from
  `import.sql`. Packaged Docker Compose explicitly keeps this disposable
  reset behavior. The k3d lab instead runs a single bootstrap Job per database when
  a schema is absent or an explicit reset is requested; normal Core, Assets, Market, World and
  Quest Pods validate their schemas without modifying data. For a schema-changing
  archive, explicit reset-aware promotion verifies the full archive and E2E
  result, refuses unfinished Expeditions, stops admission and application traffic, drains all writers, refuses unresolved
  asset workflows, then recreates Core, Assets, Market, World and Quest from matching seeds,
  bootstraps all five databases using their exact archived images, restores all
  services and HPAs, then runs the normal
  Pod-image, browser, and k6 gates. Keycloak and Redis data remain untouched. Because a data reset is not reversible by restoring an
  older image, failures after reset do not automatically roll back images.
  The seed contains
  local Accounts and Managers for user1, user2, and manager1 through
  manager10, plus a Core-only Soren fixture. User3 remains unprovisioned for
  onboarding tests. Dawnwatch Agency has six members and six heroes;
  Ironridge Exchange has four members and open market orders; Silverkeep Guild
  has three members. The seed also contains three globally available recruits,
  Broken Pass Party at the agency, World Map/Creature catalogs, four optional
  Quest definitions, rune inventory, Magic Crystals, Iron Ingots, equipped runes,
  and two feed posts. See [TEST_DATA.md](TEST_DATA.md) for all credentials and
  memberships. Seeded Managers normally have zero personal gold; User 2 has
  100,000 gold for local testing, Soren and Manager 2 have 25 gold, Manager 3
  has 20, and Manager 4 has 200 to exercise
  exact, insufficient, and ample borrowing payments. All have empty personal
  item and rune inventories except Manager 3's Magic Crystals and Manager 4's
  Iron Ingots, and a distinct personal starter
  Warrior, Mage, and Archer. These 39 heroes are not agency assets or global
  recruits. Manager-owned parties, personal Hero Expedition participation, and personal
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
  hybrid mode; there is no separate daily Traefik gateway. Core, Assets, Market, World, Quest and Keycloak
  PostgreSQL, BFF/Expedition Redis, RabbitMQ, Keycloak,
  and observability stay in k3d. Core, BFF, Expedition, Market, Assets, World, Quest, and frontend can independently use
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
  Redis, RabbitMQ, Keycloak, and application Pods. It runs browser/API
  cases and k6 market thresholds with 30-second test tokens and a
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
- Standalone Docker Compose runs Core, BFF, Market, Assets, World and Quest,
  their PostgreSQL databases, BFF session Redis and Keycloak. The optional
  Expedition override supplies Redis and RabbitMQ infrastructure; the integrated
  Map workflow uses Expedition in k3d or hybrid mode. Core remains on the private Compose network; BFF and Keycloak
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
  agency values current without requiring WebSocket or server-sent
  event connections. The separate Quest board also polls its owner service. An opt-in browser-to-BFF
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
  are enabled by k3d integration. The optional Quest board replaces Core Quest combat. The disposable k3d Playwright gate verifies Map entry, live and
  reconnected WebSocket visuals, explicit Continue, deferred Return with no
  viewer, and Core settlement without changing daily game data. See
  [Expedition settlement](EXPEDITION_SETTLEMENT.md).
  Broker setup creates settlement and acknowledgment topology before either
  service starts. Core and Expedition use separate accounts with no configure
  permission: each publishes only to its own exchange and reads only its queue.
  Private k3d Redis, RabbitMQ, and Expedition are staged. An explicit
  integration command enables Core admission/settlement, Expedition APIs, and
  the BFF WebSocket; an authenticated k3d journey passed, with the frontend Map now enabled.
  The eight-image local pipeline promotes Core, BFF, Expedition, Market, Assets,
  World, Quest and Map-enabled frontend together; a single-service lane verifies
  the other seven running images before promotion. Both paths passed archive-backed,
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
- The Quests screen reads its own service, offers one optional assignment, shows
  saved progress and recent completion/cancellation, and retries stable command
  IDs after lost responses and reloads. Acceptance never selects a Party.
- The Map selector reads World. A catalog outage prevents new entry while an
  admitted run continues from pinned data. The view names its destination, floor,
  fight result, carried assets and optional Quest progress. Clearing a dungeon
  disables auto-continue and requires Return. Entry retries reuse their IDs.
- The Heroes screen shows personal and agency Heroes, prepared Parties and away
  Parties. Rune and activity controls are locked while Heroes are on Expedition;
  the server also blocks adding Heroes to an away Party. Agency borrowing price
  remains future configuration; current Map entry accepts personal Heroes only.
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
- The seeded agency party equips a Critical Chance Rune on every hero, and
  Elara also equips a Critical Damage Rune. Each initial troll has a 10%
  critical-hit chance.
- The Agency screen shows API-loaded stackable items and runes with quantities
  and descriptions. Clicking an agency or personal Hero rune slot opens a
  drawer with the caller's personal and agency rune inventories. Each equip,
  replace, or unequip request persists immediately; the frontend updates
  optimistically without a page-wide loading state and reconciles on failure.
  Item stacks can be reserved by market orders; item equipment and additional Creature loot balance,
  and compatibility rules are not implemented yet.
- The Market screen loads the live global order book, creates buy or sell
  orders from personal or agency assets, and cancels orders owned by the
  Manager or an agency they lead. The browser keeps pending IDs in session
  storage across reloads and shows pending placement/cancellation until confirmed. It refreshes through the normal five-second browser polling.
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

A build-once local delivery lane packages tested Core, BFF, Expedition, Market and Assets
JVM images plus the frontend image in a checksummed archive. The disposable
k3d archive gate records a matching passing result before promotion is
allowed. Normal promotion imports those exact images, updates the eight k3d
application Deployments, waits for rollouts, and runs k3d browser tests. A
failed normal rollout or test restores prior image references. An explicit
`--reset-game-db` promotion requires a complete verified archive, refuses
unfinished Expeditions and asset workflows, stops all application services, and
resets Core, Assets, Market, World and Quest using
their archived images before running the same gates. `--reset-core-db` is an
alias. A reset cannot safely roll back to older images after replacing data.
