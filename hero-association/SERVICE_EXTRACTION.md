# Domain and service extraction plan

Status: in progress. This plan distinguishes scaffolded services from live
ownership: Core remains the working game backend. See
[ADR 0004](adr/0004-core-as-temporary-modular-monolith.md),
[Combat and Expedition plan](COMBAT_EXPEDITION_PLAN.md),
[Market architecture](MARKET_ARCHITECTURE.md),
and the [roadmap](ROADMAP.md).

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
| Map | Reusable field/dungeon definitions, floor layouts, and possible encounters | Not implemented; extraction planned |
| Expedition | One persistent Party per Manager, active Map run, encounter coordination, and explicit return | Private Redis encounter, admission, settlement handoff, BFF socket, and opt-in Map UI implemented; cutover pending |
| Quest | Optional objectives, rewards, and objective progress | Current quest-based flow in Core; extraction planned |
| Creature | Versioned creature definitions: stats, attacks, XP, and drop tables | In Core; extraction planned |
| Social | Feed posts; proposed future scope is manager-authored text only | In Core; extraction planned |
| Assets | Manager/agency gold, unequipped item and rune inventories, transfers, and reservations | In Core; extraction planned |
| Market | Orders, matching, trade history, and order state | In Core; extraction planned |
| Combat rules | Pure battle calculation in a library; Expedition owns active run state | Shared library in Core, sandbox, and private Expedition loop |

Map defines `FIELD` and `DUNGEON` locations, reusable layouts, and possible
Creature encounters. A Manager can enter a Map without a Quest. Expedition
owns that Manager's single persistent, nonempty Party and at most one active
Map run. The Party starts with the three personal starter heroes. It can be
edited only at the agency, after the Manager has joined or created one. The
first Map version accepts personal heroes only; agency-hero borrowing and its
fee are deferred. Quest may later attach an optional objective to a Map run,
but never becomes a prerequisite for entering a Map. Expedition runs
fights using the combat-engine library. Cities and travel remain deferred.

An Expedition has no time limit. After a winning fight, it waits for an
explicit Continue command before starting another encounter. A Manager may
request return to the agency; between fights this returns immediately, while
an active fight finishes first and no further encounter begins. A party
wipe ends the fight and prevents new encounters, but does not return the Party automatically. The Manager must
choose to return. The current Core quest flow and its borrowing fee remain
unchanged until the new Map path is cut over.

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

The active ordered work is in [COMBAT_EXPEDITION_PLAN.md](COMBAT_EXPEDITION_PLAN.md):
a shared pure combat-engine library inside an Expedition service, Redis current run
state, settlement on return, and 100/500/1,000-concurrent-fight tests. The
[Expedition contract](EXPEDITION_CONTRACT.md) defines run admission, lifecycle,
and durable Redis state; the private loop and settlement are implemented but have no live endpoint yet.
[ADR 0008](adr/0008-combat-engine-in-expedition.md) supersedes the standalone
Combat-service cutover in ADR 0007. Core remains the live quest-battle writer
until the new path passes its cutover checks. The completed sandbox work below
is retained as history; its unchecked Combat-service tasks are not active next
steps. Market and other domains remain deferred.

## Superseded standalone Combat-service checklist

### 0. Prepare the Combat boundary

- [ ] Map Combat, Expedition, Quest, Hero, and Creature tables and write paths
  to their owners. Define which battle facts Combat publishes and which
  permanent Hero and Expedition changes Core applies.
- [ ] Define stable UUIDv7 IDs, pinned input snapshots, private contracts,
  requester identity, authorization, idempotency, and failure recovery.
  Separate durable business facts from transient UI events.
- [x] Draft the private start, progression, and stream contract in
  [COMBAT_CONTRACT.md](COMBAT_CONTRACT.md); implementation and contract tests wait.
- [ ] Preserve independently buildable Core and BFF, frontend checks, browser
  E2E, k3d deployment checks, and relevant market k6 tests.

### 1. Pin battle inputs in Core

- [x] Replace hard-coded creature combat values with seeded, versioned
  definitions. New quest battles resolve the latest definition by name and
  pin its ID, version, and stats in each combatant snapshot. The canonical
  Troll starts at 2,000 health; Forest Wolf retains 120. Existing seeded
  battles preserve their original snapshots.
- [x] Recover agency resources before battle start, then pin Hero resources,
  starting stamina, level and skill baselines, class combat values including
  basic-attack mana cost, spell eligibility/cooldown state, and equipped rune
  slots with effects. Combat does not query Hero or Creature for every attack.
  Code-level spell formulas and ruleset versioning still need a cutover policy.
- [x] Use Redis as the only creature-definition cache initially; Core Creature
  data remains the source of truth. Resolve a definition once per battle, not
  once per attack or creature instance. Isolate cache eviction and memory
  from critical BFF session data. Core uses a separate disposable Redis
  instance with 60-second entries and falls back to PostgreSQL on cache errors.

### 2. Extract Combat and introduce the BFF WebSocket

- [ ] Create an independently buildable and deployable Combat service with
  its own database and credentials. Move the deterministic battle engine,
  active timers, temporary resources, and ordered battle events there.
  Quest in Core temporarily remains the encounter coordinator. Keep Core as the single
  live battle writer until cutover.
  - [x] Create the independent Combat project, isolated disposable PostgreSQL
    sandbox, and role-protected idempotent `PREPARED` battle-start record.
    No Core/BFF traffic is routed to it yet.
  - [x] Mirror the pure deterministic engine and its tests into Combat.
    Verify exact parity with Core using the read-only parity script while
    Core remains the sole live battle writer.
  - [x] Validate an opening typed engine snapshot and persist its canonical
    state alongside the immutable idempotent start request.
  - [x] Pin Hero identity, class, levels, stamina, and equipped rune slots
    plus Creature definition/version and base XP in ordered start inputs.
    Validate them against the engine snapshot, including Mage spell eligibility.
  - [x] Add bounded, role-protected manual advancement in the isolated Combat
    sandbox. Persist snapshot, sequenced UI events, and chronological progression
    facts in one transaction; queue fact batches in its database outbox.
  - [x] Add an opt-in Combat worker with a persisted wall-clock anchor,
    bounded catch-up, and row-locked per-battle transactions. Keep it off by
    default until the internal start and fact-delivery paths are connected.
  - [x] Add an opt-in Combat outbox publisher using ordered, versioned RabbitMQ
    batch messages, durable routing and publisher confirms. Failed sends stay
    pending; a crash between broker confirmation and database commit can
    redeliver the same batch ID.
  - [x] Add an opt-in Core RabbitMQ intake consumer and singular inbox table.
    Validate the versioned envelope, deduplicate by batch UUID, reject
    conflicting replays, store gaps unapplied, and acknowledge only after
    the database transaction commits. It does not mutate Hero/Quest state.
  - [x] Add a manual, transactionally idempotent Core Hero-side applier for
    registered non-Quest battles. It waits for the next sequence and applies
    stamina, skill, XP, and terminal resources; no live caller exists yet.
  - [ ] Connect Combat-owned starts and registration only after retiring the
    competing Core writer for those battles. Add an Expedition outcome
    consumer, private event stream, ruleset cutover policy, and complete
    local/k3d cutover checks.
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

### 3. Add Map and Expedition

- [ ] Add versioned Map definitions for `FIELD` and `DUNGEON`, including
  reusable dungeon floors and possible encounters referencing Creature IDs.
  Start with a fixed Troll encounter. Pin the used Map version to the run.
  Cities, travel, and free roaming remain deferred.
- [ ] Extract Expedition with its own data and credentials. It owns one
  persistent Party per Manager, seeded with the three personal starter heroes,
  and one active Map run at most. Require agency membership to edit the Party
  or enter a Map; reject agency-owned heroes in this first Map flow. Editing
  is allowed only while the Party is at the agency, and it may never be empty.
- [ ] Implement explicit return: immediately between battles, or after the
  current battle finishes if one is active. A wipe ends the battle and stops
  encounters but leaves the Party on the Map until return is requested.
  The server continues running when the browser disconnects.
- [ ] Wire the BFF and frontend to Map selection, Party editing, Expedition
  commands, and the Combat WebSocket. Verify authorization, reconnect, a wipe,
  and return during battle before removing the old quest-only frontend path.

## Deferred domain extractions

### 4. Add optional Quest objectives

- [ ] Model creature kill-count, boss-defeat, and dungeon-completion
  objectives. A Quest may be location-independent, allow several eligible
  Maps, or target a dungeon; it never gates entry to a Map. Apply progress
  from authoritative Combat and Expedition outcomes, not browser reports.

### 5. Establish Assets contracts in Core

- [ ] Resolve ownership of item/rune definitions and equipped runes. Give
  Assets explicit Manager/agency ownership and atomic, idempotent reserve,
  release, transfer, and settlement operations. Make these available to
  Market through a private Core API. Do not create a separate Assets service
  just to start Market.
- [ ] Define how future battle consumables are reserved or consumed without
  double spending; this does not block the initial read-only combat stream.

### 6. Extract Market

- [ ] Follow [Market architecture](MARKET_ARCHITECTURE.md): Market owns its
  order database; Assets in Core remains the resource and reservation
  authority during the first extraction.
- [ ] Route the existing `/api/v1/market/**` contract through BFF to Market.
  Retain k3d Envoy's five order-placement attempts per second per
  authenticated user; local Traefik has no market limit.
- [ ] Test duplicate requests, timeouts, partial fills, cancellation races,
  restarts, reconciliation, and resource release. Remove Core's Market writer
  only after the new path works; never share Core's database with Market.

### 7. Extract the remaining domains and retire Core

- [ ] Extract Assets after defining its ledger, reservation protocol, agency
  permission checks, and failure recovery. It takes over resource authority
  for Market, transfers, and quest payouts.
- [ ] Move Quest, Hero, Agency, Account, Creature, and Social out of
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
