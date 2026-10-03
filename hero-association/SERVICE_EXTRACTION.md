# Domain and service extraction plan

Current World/Quest cutover: [WORLD_QUEST_ARCHITECTURE.md](WORLD_QUEST_ARCHITECTURE.md)
is authoritative for versioned Map admission, optional objectives and return
rewards. Core's earlier Quest combat, borrowing payment and Creature cache
workflows described below are retired. Return aggregates now use schema version
2 with pinned Map metadata and nullable Quest progress.

Status: staged extraction. Core owns identity, agency and permanent Hero state;
Assets, Market, World, Quest and Expedition own their extracted domains. See
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
| Hero | Recruited heroes, permanent resources, XP, skills, and equipment eligibility | In Core; extraction planned |
| Map | Reusable field/dungeon definitions, floor layouts, and possible encounters | Versioned definitions in World |
| Expedition | One persistent Party per Manager, active Map run, encounter coordination, and explicit return | Player-facing Map runs in Expedition; Map enabled by default in pipeline and hybrid frontend |
| Quest | Optional objectives, rewards, and objective progress | Optional assignments, objectives and reward recovery in Quest |
| Creature | Versioned creature definitions: stats, attacks, XP, and drop tables | Immutable versions in World |
| Social | Feed posts; proposed future scope is manager-authored text only | In Core; extraction planned |
| Assets | Manager/agency gold and inventories; Hero-owned equipped runes; transfers and reservations | Extracted to `hero-association-assets`; local six-image cutover complete |
| Market | Orders, matching, trade history, and order state | Extracted to `hero-association-market` |
| Combat rules | Pure battle calculation in a library; Expedition owns active run state | Shared library in Core and Expedition |

Map defines `FIELD` and `DUNGEON` locations, reusable layouts, and possible
Creature encounters. A Manager can enter a Map without a Quest. Expedition
owns that Manager's single persistent, nonempty Party and at most one active
Map run. The Party starts with the three personal starter heroes. It can be
edited only at the agency, after the Manager has joined or created one. The
first Map version accepts personal heroes only; agency-hero borrowing and its
fee are deferred. Quest pins an optional assignment at admission and banks authoritative progress
on Return; it never becomes a prerequisite for entering a Map. Expedition runs
fights using the combat-engine library. Cities and travel remain deferred.

An Expedition has no time limit. After a winning fight, it waits for an
explicit Continue command before starting another encounter. A Manager may
request return to the agency; between fights this returns immediately, while
an active fight finishes first and no further encounter begins. A party
wipe ends the fight and prevents new encounters, but does not return the Party automatically. The Manager must
choose to return. Completed dungeons also wait for Return. Core Quest combat
and its borrowing-fee payment are retired; future Expedition borrowing remains deferred.

`hero-association-assets` implements Assets.
Manager and agency own gold, item stacks, and unequipped rune inventories;
a Hero owns only its equipped runes, without gold or a loose inventory.
Assets must be queryable by Manager, agency, or Hero owner ID. Every member
of an agency may move that agency's runes to a Hero; unequipping returns the
rune to the acting Manager by default. Existing leader-only agency gold and
Market permissions are unchanged. Agency owns membership and leadership;
Hero owns Hero identity, progression, and at-agency eligibility. Equipped
rune ownership and slot assignment belong in Assets when extracted.
Assets owns item and rune definitions; Core receives read-only projections.
Agency upgrades, quest state, and market orders are outside Assets.

The existing feed supports more than manager-authored text. Simplifying it
would be a separate behavior change, not a completed part of this plan.

## Implementation order and progress

The player-facing Map/Expedition cutover is complete in k3d. The
[Expedition contract](EXPEDITION_CONTRACT.md) defines its run admission,
lifecycle, and durable Redis state.
[ADR 0008](adr/0008-combat-engine-in-expedition.md) supersedes the standalone
Combat-service cutover in ADR 0007. Expedition owns Map fights. The standalone
Combat sandbox and its Core fact inbox are retired. Combat load tests remain
deferred. World and optional Quest objectives are implemented; their verified
eight-image cutover is recorded in WORLD_QUEST_ARCHITECTURE.md. The earlier
Assets extraction and six-image local cutover are complete. Market's order book and recovery
are already separate. Assets owns economic mutations; Core retains player
permission decisions and durable Hero/Expedition workflows. See the
[Assets architecture](ASSETS_ARCHITECTURE.md) and
[recovery protocol](ASSETS_RECOVERY.md). Other service extractions remain
separate work.

## Retired standalone Combat sandbox

The former Combat-service prototype and its separate database, broker, and
Core fact inbox are removed. ADR 0007 remains as the historical superseded
decision; ADR 0008 defines the current library-in-Expedition approach.

## Deferred domain extractions

### 4. Add optional Quest objectives (implemented)

- [x] Model creature kill-count, boss-defeat, and dungeon-completion
  objectives. A Quest may be location-independent, allow several eligible
  Maps, or target a dungeon; it never gates entry to a Map. Apply progress
  from authoritative Expedition combat outcomes, not browser reports.

### 5. Establish Assets contracts in Core (completed before extraction)

The first private Core service contract now reserves Manager or agency gold
and item stacks under UUIDv7 keys, releases unfilled quantities, and settles
partial trades atomically. Reservations and operation receipts make exact
retries idempotent; row locks protect competing requests. This is an internal
boundary with a dedicated private HTTP API called by the separate Market
service. At this completed boundary milestone, Assets was still in Core.
Gold transfers have their own atomic,
idempotent receipts. See
[Assets contract](ASSETS_CONTRACT.md).

- [x] Add and test the internal reservation, release, and trade-settlement
  contract in Core, including duplicate requests and concurrent spending.
- [x] Define Manager, agency, and equipped-Hero rune ownership and test the
  Core transfer commands.
- [x] Assign item/rune definitions to the Assets boundary (now extracted).
  Enforce Manager/agency permissions on private reservation commands and
  retain the existing Hero equipment authorization. Add idempotent gold
  transfer receipts and expose reservation/settlement through a private
  Core API with a dedicated service credential.
- [x] Define durable placement, settlement, cancellation, and retry recovery.
  Add Core status/receipt reads and a permanent closure primitive, including
  close-before-reserve and concurrent retry checks. The Market worker and
  pending-state frontend are delivered during extraction.
- [ ] Define how future battle consumables are reserved or consumed without
  double spending; this does not block the initial read-only combat stream.

### 6. Extract Market (completed)

- [x] Follow [Market architecture](MARKET_ARCHITECTURE.md): Market owns its
  order database; Assets in Core remains the resource and reservation
  authority during the first extraction.
- [x] Route the existing `/api/v1/market/**` contract through BFF to Market.
  Retain k3d Envoy's five order-placement attempts per second per
  authenticated user in both full k3d and hybrid development.
- [x] Test duplicate requests, timeouts, partial fills, cancellation races,
  restarts, reconciliation, and resource release. Remove Core's Market writer
  only after the new path works; never share Core's database with Market.

### 7. Extract the remaining domains and retire Core

- [x] Define Assets mutation/read ownership and permission/recovery contracts.
  See [Assets architecture](ASSETS_ARCHITECTURE.md).
- [x] Implement the independently built Assets service and separate database;
  move wallets, inventories, catalogs, equipment, reservations and receipts.
- [x] Complete exact six-image isolated verification and local cutover of Core,
  Assets and Market.
- [x] Move Creature and Map definitions to World and optional objectives to
  Quest, with reward credits owned by Assets.
- [ ] Move Hero, Agency, Account, and Social out of
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
