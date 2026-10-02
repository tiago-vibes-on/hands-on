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
| Hero | Recruited heroes, permanent resources, XP, skills, and equipment eligibility | In Core; extraction planned |
| Map | Reusable field/dungeon definitions, floor layouts, and possible encounters | Not implemented; extraction planned |
| Expedition | One persistent Party per Manager, active Map run, encounter coordination, and explicit return | Player-facing k3d Map cutover complete; ordinary local Map remains opt-in |
| Quest | Optional objectives, rewards, and objective progress | Current quest-based flow in Core; extraction planned |
| Creature | Versioned creature definitions: stats, attacks, XP, and drop tables | In Core; extraction planned |
| Social | Feed posts; proposed future scope is manager-authored text only | In Core; extraction planned |
| Assets | Manager/agency gold and inventories; Hero-owned equipped runes; transfers and reservations | In Core; extraction planned |
| Market | Orders, matching, trade history, and order state | In Core; extraction planned |
| Combat rules | Pure battle calculation in a library; Expedition owns active run state | Shared library in Core and Expedition |

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
Manager and agency own gold, item stacks, and unequipped rune inventories;
a Hero owns only its equipped runes, without gold or a loose inventory.
Assets must be queryable by Manager, agency, or Hero owner ID. Every member
of an agency may move that agency's runes to a Hero; unequipping returns the
rune to the acting Manager by default. Existing leader-only agency gold and
Market permissions are unchanged. Agency owns membership and leadership;
Hero owns Hero identity, progression, and at-agency eligibility. Equipped
rune ownership and slot assignment belong in Assets when extracted.
Item/rune definition ownership is still to be decided before extraction.
Agency upgrades, quest state, and market orders are outside Assets.

The existing feed supports more than manager-authored text. Simplifying it
would be a separate behavior change, not a completed part of this plan.

## Implementation order and progress

The player-facing Map/Expedition cutover is complete in k3d. The
[Expedition contract](EXPEDITION_CONTRACT.md) defines its run admission,
lifecycle, and durable Redis state.
[ADR 0008](adr/0008-combat-engine-in-expedition.md) supersedes the standalone
Combat-service cutover in ADR 0007. Expedition owns Map fights; Core remains
the Quest-battle writer until Quest is redesigned. The standalone Combat
sandbox and its Core fact inbox have been retired. Combat load tests and
optional Quest objectives are deferred. The current bounded work is the
Assets contract inside Core; Market and other service extractions remain
deferred.

## Retired standalone Combat sandbox

The former Combat-service prototype and its separate database, broker, and
Core fact inbox are removed. ADR 0007 remains as the historical superseded
decision; ADR 0008 defines the current library-in-Expedition approach.

## Deferred domain extractions

### 4. Add optional Quest objectives

- [ ] Model creature kill-count, boss-defeat, and dungeon-completion
  objectives. A Quest may be location-independent, allow several eligible
  Maps, or target a dungeon; it never gates entry to a Map. Apply progress
  from authoritative Combat and Expedition outcomes, not browser reports.

### 5. Establish Assets contracts in Core

The first private Core service contract now reserves Manager or agency gold
and item stacks under UUIDv7 keys, releases unfilled quantities, and settles
partial trades atomically. Reservations and operation receipts make exact
retries idempotent; row locks protect competing requests. This is an internal
boundary only: the existing public Market writer and gold-transfer path do
not call it yet, and there is no Market-to-Core endpoint or separate Assets
service. See [Assets contract](ASSETS_CONTRACT.md).

- [x] Add and test the internal reservation, release, and trade-settlement
  contract in Core, including duplicate requests and concurrent spending.
- [x] Define Manager, agency, and equipped-Hero rune ownership and test the
  Core transfer commands.
- [ ] Decide ownership of item/rune definitions. Give Assets explicit
  Manager/agency/Hero authorization and an idempotent transfer contract.
  Expose validated reservation and settlement operations to Market through
  a private Core API. Do not create a separate Assets service just to start
  Market.
- [ ] Define how future battle consumables are reserved or consumed without
  double spending; this does not block the initial read-only combat stream.

### 6. Extract Market

- [ ] Follow [Market architecture](MARKET_ARCHITECTURE.md): Market owns its
  order database; Assets in Core remains the resource and reservation
  authority during the first extraction.
- [ ] Route the existing `/api/v1/market/**` contract through BFF to Market.
  Retain k3d Envoy's five order-placement attempts per second per
  authenticated user in both full k3d and hybrid development.
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
