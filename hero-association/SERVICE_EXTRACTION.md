# Domain and service extraction plan

Status: planning. This is an implementation plan, not a claim that the proposed
services already exist. Game Core remains the working backend. See
[ADR 0004](adr/0004-core-as-temporary-modular-monolith.md),
[Market architecture](MARKET_ARCHITECTURE.md), and the [roadmap](ROADMAP.md).

## Strategy

Keep one working Core, not a parallel `core-v2`. Define domain ownership and
contracts inside Core first, then move every responsibility out of Core in
stages. A domain boundary does not automatically require a separate service;
the final service grouping remains a design decision. The BFF
remains the browser-facing entry point and Keycloak remains the identity
provider. Internal extraction alone does not require a public API `/v2`.

Every extracted service owns its data and credentials. Other services use its
private API or events, not its tables. Until Flyway is introduced, local and
pre-production data may be reset and reseeded; runtime operations still need
idempotency, retries, and recovery.

## Domain boundaries

| Domain | Owns | Current state |
| --- | --- | --- |
| Account | Manager profile and Keycloak-subject mapping, not login credentials | In Core; extraction planned |
| Agency | Membership, leadership, permissions, and upgrades | In Core; extraction planned |
| Hero | Recruited heroes, permanent resources, XP, skills, and loadouts | In Core; extraction planned |
| Place | Reusable field/dungeon definitions, floor layouts, and possible encounters | Not implemented; Core first, then extract |
| Quest | Objectives, parties, rewards, runs, and objective/floor progress | In Core; extraction planned |
| Creature | Versioned creature definitions: stats, attacks, XP, and drop tables | In Core; extraction planned |
| Social | Feed posts; proposed future scope is manager-authored text only | In Core; extraction planned |
| Assets | Manager/agency gold, unequipped item and rune inventories, transfers, and reservations | In Core; extraction planned |
| Market | Orders, matching, trade history, and order state | In Core; extraction planned |
| Combat | Active battles, timers, temporary HP/mana, and battle events | In Core; extraction planned |

Place is not implemented yet. It will initially define `FIELD` and `DUNGEON`
locations in Core, then leave Core during extraction. Place owns reusable
layouts and possible Creature encounters. Quest may be location-independent,
allow several eligible Places, or target one dungeon; it owns objectives and
per-run progress. Combat owns active fights. Cities and travel remain deferred.

`hero-association-assets` is the planned service name for Assets.
It serves two distinct owners: a Manager and an agency. An agency leader may
manage agency assets, but those assets do not become personal property. Agency
owns membership and leadership decisions; Assets enforces the resulting
authorization for agency operations. Heroes, agency upgrades, quest state,
and market orders are outside Assets. Decide ownership of item/rune definitions
and equipped runes before extracting Assets.

The existing feed supports more than manager-authored text. Simplifying it
would be a separate behavior change, not a completed part of this plan.

## Implementation order and progress

Combat extraction now precedes Market extraction. Core remains the live battle
authority until the new Combat service, BFF socket, and frontend path pass
their cutover checks. The phases below are ordered; unchecked tasks are not
implemented.

### 0. Prepare the Combat boundary

- [ ] Map Combat, Quest, Hero, and Creature tables and write paths to their
  owners. Define which battle facts Combat publishes and which permanent
  Hero and Quest changes Core applies.
- [ ] Define stable UUIDv7 IDs, pinned input snapshots, private contracts,
  requester identity, authorization, idempotency, and failure recovery.
  Separate durable business facts from transient UI events.
- [ ] Preserve independently buildable Core and BFF, frontend checks, browser
  E2E, k3d deployment checks, and relevant market k6 tests.

### 1. Pin battle inputs in Core

- [ ] Replace hard-coded creature combat values with versioned definitions.
  Start with one canonical Troll at 2,000 health; other statistics remain
  provisional data. Pin the selected version to a quest run and snapshot it
  in each battle, so later balancing changes do not alter active battles.
- [ ] Snapshot the party's Hero resources, levels, class combat attributes,
  spells, and equipped runes at quest start. Combat must not query Hero or
  Creature for every attack. Flexible Places and Quest objectives are not
  prerequisites for this first extraction.
- [ ] Use Redis as the only creature-definition cache initially; Core Creature
  data remains the source of truth. Resolve a definition once per battle, not
  once per attack or creature instance. Isolate cache eviction and memory
  from critical BFF session data.

### 2. Extract Combat and introduce the BFF WebSocket

- [ ] Create an independently buildable and deployable Combat service with
  its own database and credentials. Move the deterministic battle engine,
  active timers, temporary resources, and ordered battle events there.
  Quest in Core remains the encounter coordinator. Keep Core as the single
  live battle writer until cutover.
- [ ] Deliver durable, uniquely identified battle outcomes and progression
  facts to Core with retry and idempotent application. Plan RabbitMQ with
  outbox/inbox recovery for cross-domain facts; do not publish every visual
  hit or call another service for every attack. Preserve complete progression
  facts even when bounded UI event history is trimmed.
- [ ] Add the browser-facing WebSocket endpoint to BFF, not Core or Combat.
  BFF bridges to Combat through a private authenticated stream. The first
  release sends authoritative snapshots and ordered events; automatic
  attacks and spells remain server-driven. Define a versioned envelope,
  battle/event sequence IDs, client command IDs, and acknowledgment/rejection
  semantics for later optional actions such as a consumable party buff.
  Do not implement item effects or speculative future simulation here.
- [ ] Authenticate the BFF socket with the existing session, validate Origin,
  protect the handshake against cross-site requests, and authorize battle
  subscriptions. Recheck session expiry and logout. Combat validates any
  future command's actor, timing, and effect; Assets must coordinate future
  item consumption idempotently. Keep browser credentials out of URLs.
- [ ] Reconnect with an authoritative snapshot and resume events where
  available; tolerate trimmed history. Battles continue without viewers.
  Use ping/pong to detect dead sockets, with gateway idle timeouts above the
  heartbeat interval. Test duplicate delivery, forbidden parties, expired
  sessions, cross-site handshakes, slow clients, multi-replica routing, and
  reconnects.
- [ ] Switch the expanded combat view from two-second sync polling to the
  BFF socket; keep unrelated five-second agency, market, and feed polling.
  Configure local and k3d gateways for upgrades and timeouts. Run contract,
  backend, frontend, browser E2E, and k3d checks before cutover. Only then
  remove Core's combat writer, worker, and sync endpoint.

### 3. Add Place and flexible Quest objectives

- [ ] Add Place definitions for `FIELD` and `DUNGEON`, including reusable
  dungeon floors and possible encounters referencing Creature IDs. Allow
  optional Place restrictions on Quest objectives and pin each used Place
  version to the run. Cities, travel, and free roaming remain deferred.
- [ ] Model creature kill-count, boss-defeat, and dungeon-completion
  objectives. Apply progress from authoritative Combat and Quest outcomes;
  one quest may span multiple encounters. Quest owns progress, not Place.

### 4. Establish Assets contracts in Core

- [ ] Resolve ownership of item/rune definitions and equipped runes. Give
  Assets explicit Manager/agency ownership and atomic, idempotent reserve,
  release, transfer, and settlement operations. Make these available to
  Market through a private Core API. Do not create a separate Assets service
  just to start Market.
- [ ] Define how future battle consumables are reserved or consumed without
  double spending; this does not block the initial read-only combat stream.

### 5. Extract Market

- [ ] Follow [Market architecture](MARKET_ARCHITECTURE.md): Market owns its
  order database; Assets in Core remains the resource and reservation
  authority during the first extraction.
- [ ] Route the existing `/api/v1/market/**` contract through BFF to Market.
  Retain k3d Envoy's five order-placement attempts per second per
  authenticated user; local Traefik has no market limit.
- [ ] Test duplicate requests, timeouts, partial fills, cancellation races,
  restarts, reconciliation, and resource release. Remove Core's Market writer
  only after the new path works; never share Core's database with Market.

### 6. Extract the remaining domains and retire Core

- [ ] Extract Assets after defining its ledger, reservation protocol, agency
  permission checks, and failure recovery. It takes over resource authority
  for Market, transfers, and quest payouts.
- [ ] Move Place, Quest, Hero, Agency, Account, Creature, and Social out of
  Core in an order based on their dependencies. Decide whether each needs its
  own service or belongs with a related domain; none remains in Core
  permanently.
- [ ] Retire Core only after every responsibility has a new owner and
  end-to-end flows work without Core's APIs or database.

## Rule for every cutover

Document the owner and contract; build the new service with isolated data;
run contract, integration, browser, and relevant load tests; switch the
single authoritative write path; verify local and k3d workflows; then remove
the old Core path. Do not keep two writers for one domain or declare a
migration complete while pending operations can be lost. Revisit this order
if a split creates chatty calls or forces coordinated deployments.
