# Expedition run contract and Redis state

Current World/Quest cutover: [WORLD_QUEST_ARCHITECTURE.md](WORLD_QUEST_ARCHITECTURE.md)
is authoritative for versioned Map admission, optional objectives and return
rewards. Core's earlier Quest combat, borrowing payment and Creature cache
workflows described below are retired. Return aggregates now use schema version
2 with pinned Map metadata and nullable Quest progress.

Status: the player-facing Map cutover and the first seven steps of the
[implementation plan](COMBAT_EXPEDITION_PLAN.md) are complete; combat load
testing remains deferred. Core exposes authenticated internal
admission and idempotent settlement. Expedition has a server-side Core client,
entry coordinator, orphan fence, and owner-scoped HTTP commands. BFF routes
those commands, but the Expedition player API is disabled by default. There
is a feature-flagged frontend Map page, enabled in the k3d lab.
Core Quest combat is retired. The BFF has a
disabled-by-default socket that validates session, exact Origin, and run
ownership before sending a reconnect snapshot. A local scheduler sends changed
visual snapshots to subscribed sockets from Expedition's private Redis-only
API; no per-frame Core read or durable per-hit event is involved.
[ADR 0008](adr/0008-combat-engine-in-expedition.md) governs this design.

The current run snapshot pins a complete World Map/Creature plan, optional
Quest assignment/progress, Hero resources, progression and rune effects from
Core admission, carried loot, the opening fight snapshot, RNG version/seed,
and 1x XP/skill/drop rates. Creature seeds currently have no economic drops;
capacity and configurable event rates remain future work. The settlement
payload is derived from the frozen run on every retry rather than stored as a
second copy inside that run. The due index is rebuildable and terminal
commits are fenced by a Redis lease and state version. See the
[private service README](backend/hero-association-expedition/README.md).

## Ownership and admission

Expedition is the target owner of one persistent, nonempty Party and at most
one active Map run per Manager, across all agencies. The first Map flow uses
only that Manager's personal heroes, even if the current Core Party model also
permits agency-owned heroes and multiple Parties. Party editing is allowed
only at the agency, never during an active run. Map entry requires current
agency membership, ownership of the selected Party, at least one living
personal hero in it, and no active Expedition or asset workflow using those Heroes.
Agency leadership does not grant access to another Manager's Party or run.
The private service validates a token intended for Expedition and resolves
`managerId` from the authenticated subject through a trusted Account mapping;
it never trusts a browser-supplied owner ID. BFF remains the browser boundary.

Core temporarily owns Party writes and admission. The shared permanent-Hero
reservation fences roster, activity, and loadout changes while away. Its
admission operation atomically validates membership and Hero
availability, apply accrued agency recovery once, reserve the selected
Heroes against training/loadout changes, and returns one consistent
baseline keyed idempotently by `expeditionId`. It also releases an
orphaned reservation if Redis run creation is proven not to have succeeded.
Quest independently pins the optional assignment under the same Expedition ID.

That one private admission response supplies the current Hero IDs, class,
total XP, six-decimal skill-point totals, health, mana, stamina milliseconds,
and equipped rune slots/effects. It supplies only expedition-relevant Assets
inputs: owned equipment used by the Party and any items explicitly taken
into the run. The first slice takes no consumables, so carried gold/items/
runes start empty; the Manager wallet and stored inventory remain in Assets.
Do not copy an entire wallet into Redis or later overwrite it with a stale
entry balance. Permanent owners receive deltas at settlement, not per-hit
updates. Core/Assets is not queried again for Hero or Assets baselines during
the run. World supplies every referenced Creature version with the Map plan
at admission; no World read is needed for later encounters in that run.

## Commands and lifecycle

The following is the Expedition command contract. Owner-scoped HTTP commands
and BFF routing exist, but the player API remains disabled by default; the
feature-flagged browser Map page uses those commands. The opt-in BFF socket sends an
owner-checked snapshot on connection or reconnection and changed fight
visuals while the run is subscribed.
Every mutation has a UUIDv7 `commandId`, and an active-run mutation includes
`expectedVersion`. The service verifies the authenticated Manager owns the
run and Party. It returns the authoritative run snapshot and new version;
repeating the same command ID and payload returns the same logical outcome
and the current authoritative snapshot, while
reusing an ID with a different payload is a conflict.

| Operation | Preconditions and effect |
| --- | --- |
| `Start(mapId, partyId, commandId)` | Reserve the entry baseline; atomically create the Manager's active-run index and UUIDv7 run. A different active run is `409 Conflict`. Start the first pinned encounter without requiring Continue. |
| `Get(runId)` | Return the current snapshot to the owner; do not advance combat. A foreign run is not disclosed. |
| `Continue(runId, commandId, expectedVersion)` | Only from `AWAITING_CONTINUE`, with living Heroes and no return request. Pin the next encounter and enter `FIGHTING`. It never starts automatically after a win. |
| `Return(runId, commandId, expectedVersion)` | From `AWAITING_CONTINUE` or `WIPED`, freeze for settlement. During `FIGHTING`, persist `returnRequested=true`; finish that fight, then freeze without another encounter. |

The browser connects to `/ws/v1/expeditions/{expeditionId}` through the BFF
with its existing session cookie. The upgrade requires a matching Origin and
owner-scoped Expedition Get. The first frame is
`{"type":"snapshot","snapshot":<public-run-view>}`; subsequent frames use
the same shape only when the view changes. Each BFF replica polls only runs
with local subscribers, at most once per second per run, through the private
`GET /internal/v1/expedition-visuals/{ownerManagerId}/{expeditionId}` endpoint.
That endpoint reads Expedition Redis and requires the shared uncommitted
`HERO_ASSOCIATION_EXPEDITION_BFF_SERVICE_KEY` in a service-only header.
The projector reads the planned encounter timeline for visual health, mana,
and recent hits, with bounded elapsed time and events.
No per-frame Core query, Redis mutation, or durable combat event is added.
This replay strategy is provisional until the 100/500/1,000-fight load tests.

`stateVersion` starts at 1 and increases on every durable run mutation.
An atomic Redis script/function compares the expected version, owner,
current phase, and command receipt before changing the run. Stale versions
return `409 Conflict` with the latest version/snapshot; malformed commands
are `400`; missing or foreign runs are `404`; unauthenticated callers are
`401`. A duplicate command with the same ID and payload is answered before
the stale-version check. No command trusts client-calculated XP, damage,
loot, stamina, or time.

The phases are `FIGHTING`, `AWAITING_CONTINUE`, `DUNGEON_COMPLETED`, `WIPED`, and
`SETTLEMENT_PENDING`. The Party is at the agency when no active run exists.
A winning fight moves to `AWAITING_CONTINUE`, or `DUNGEON_COMPLETED` after the
last dungeon encounter; a pending Return moves to `SETTLEMENT_PENDING`.
A wipe moves to `WIPED`, except that a pending
Return moves to `SETTLEMENT_PENDING`. A wipe does not automatically return.
`SETTLEMENT_PENDING` accepts no new fight commands. The private handoff and
owner-application acknowledgment are defined in
[Expedition settlement](EXPEDITION_SETTLEMENT.md); closing the run requires
that acknowledgment.
Membership or roster changes must be blocked while the reservation is held;
if an inconsistency is detected, deny Continue but preserve owner access to
Get and Return for recovery.

## Authoritative Redis keys

Use a dedicated Expedition Redis instance/keyspace, separate from BFF
sessions and disposable Creature cache. The braces below are literal Redis
hash tags, so the active index, run, and command receipts for one Manager
can be updated atomically even with clustered Redis later:

```text
ha:expedition:v1:{<manager-uuid>}:active                 -> run UUIDv7
ha:expedition:v1:{<manager-uuid>}:run:<run-uuid>         -> one UTF-8 JSON snapshot
ha:expedition:v1:{<manager-uuid>}:command:<command-uuid> -> payload digest, outcome, resulting version
```

The active index and run have **no TTL** until settlement is confirmed.
Command receipts contain no full run snapshots, have a bounded retention
period (initially 24 hours), and must be written atomically with their
mutation. After that period, a stale
`expectedVersion` still prevents a duplicate Continue or Return; callers
must not retry an ancient Start as though it were the same operation.
A worker due-time index may be added in step 4, but it is derived and
rebuildable, never an alternative source of run truth. Any cross-slot due
index update may lag the run write; recovery must repair it.

The run JSON has these required fields; nested combat data uses an explicit
serializer for the shared engine's `CombatBattleSnapshot`, not arbitrary Java
object serialization:

| Field | Type and invariant |
| --- | --- |
| `schemaVersion` | Integer `2`; reject unknown versions rather than silently dropping fields. |
| `stateVersion` | Positive, monotonically increasing integer for atomic compare-and-set. |
| `expeditionId`, `ownerManagerId`, `agencyId`, `partyId` | UUIDv7 strings; immutable for the run. |
| `mapId`, `mapVersion`, `encounterIndex` | Exact pinned Map identity and current one-based encounter position. |
| `world` | Complete immutable Map plan and every exact Creature version it references; kind, floor and encounter layout derive from this plan. |
| `quest` | Nullable pinned optional assignment, eligibility, banked progress and current bounded objective progress. |
| `phase`, `returnRequested`, `startedAt` | Lifecycle phase, Boolean return intent, and admission UTC instant. |
| `heroes` | Ordered current per-Hero class, total XP, six-decimal Melee/Distance/Magic/Shield points, current health/mana, and stamina milliseconds. These are unbanked while the run is active. |
| `creature` | Compatibility projection of the first Creature spawn; actual combat and loot use the complete World plan. |
| `carried` | Current unbanked gold and item/rune ID-to-quantity totals; starts empty. Capacity rules remain future work. |
| `fight` | Present only in `FIGHTING`: UUIDv7 fight ID, combat ruleset version, active XP/skill/drop multipliers, RNG algorithm version and hidden seed, UTC start time, and full engine-restorable opening `CombatBattleSnapshot`. |
| `lastOutcome` | At most one compact result for the most recent encounter, cleared on Continue; not a fight history or replay log. |

The `SETTLEMENT_PENDING` snapshot is the frozen aggregate. A separate same-slot
pending marker is written atomically; the wire payload derives from the run
by `expeditionId`. Broker and owner acknowledgments are tracked separately.

The Hero and carry values above are the **latest current state**, not a list
of completed fights. Do not retain per-hit rows, finished-fight snapshots,
or a growing event log in Redis. Visual events are ephemeral and a viewer
reconnects from the current authoritative snapshot. Store integer units for
gold, quantities, XP, health, mana, and stamina; store skill points as exact
decimal strings at scale 6. Validate nonnegative totals and unique Hero IDs
on every admitted or restored snapshot.

## Restart and storage safety

The fight's opening engine snapshot, pinned inputs, start time, ruleset
version, and RNG seed are the restart boundary. A worker may hold live combat
objects in memory as a disposable calculation, but no singleton Java state
is authoritative. On process restart, workers discover `FIGHTING` runs,
rebuild scheduling, restore the opening snapshot, and deterministically
re-simulate elapsed combat time with the same random stream. Only one fenced
worker may commit the terminal state with a version check. A Return written
during a fight must survive and be merged into that terminal commit, not
overwritten by a worker using an older version. No Hero/Assets owner update
or RabbitMQ settlement occurs during replay.

`AWAITING_CONTINUE` and `WIPED` remain parked across restarts. A pending
settlement resumes its retry/reconciliation path; it is never replaced with
the entry baseline. Unknown schema/ruleset versions, missing index/run pairs,
or corrupt snapshots fail closed and require investigation. Do not invent a
new run from Core's last permanent Hero state: that would lose unbanked
progress. Lost visual events may be discarded; an authoritative snapshot
must let the browser recover its display.

Before cutover, provision persistent Redis storage with AOF durability,
`noeviction`, no active-key expiration, backups, and memory/error monitoring.
For acknowledged run-boundary writes, require a durability policy that
survives a Redis process restart (initially `appendfsync always`; measure its
cost in the planned load tests). If Redis cannot accept a durable write,
fail the command/terminal commit rather than acknowledging progress that
only exists in memory. This is not a guarantee against loss of the host disk;
recovery drills and backups remain necessary. Redis documents the
[per-write AOF durability tradeoff](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)
and that [`noeviction` rejects writes at the memory limit](https://redis.io/docs/latest/develop/reference/eviction/).

## Acceptance checks for later implementation

- Two simultaneous Starts for one Manager yield one run; two Managers cannot
  claim the same Hero or use another Manager's Party.
- A stale version or conflicting command ID cannot cause two encounters,
  overwrite Return, or create a second active run.
- A crash after admission, after Redis creation, during a fight, and after
  terminal commit has an idempotent recovery path; orphan reservations are
  reconciled, never silently released while a run may exist.
- Restarting Expedition or Redis resumes only the latest durable run state.
  A finished fight never awards XP/loot twice; no next fight begins without
  Continue, and a wiped Party stays on the Map until Return.
- No attack or periodic combat tick writes Core SQL or a durable event row.
  The run remains bounded in size as encounters accumulate.
