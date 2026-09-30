# ADR 0008: Run the combat engine inside Expedition

- Date: 2026-09-30
- Status: Accepted; implementation in progress
- Supersedes: ADR 0007 standalone Combat-service cutover and per-battle
  persistence plan

## Context

Core currently persists live quest-combat state on a five-second worker and
also advances it when an expanded browser view calls the two-second sync API.
An isolated Combat-service sandbox was built with its own PostgreSQL battle
table, one-second worker, event rows, and progression outbox. It has no live
caller, so it does not constrain the player-facing design.

A Map run can contain several fights. The Party's current resources, XP,
skills, stamina, and carried assets must carry into the next fight, but their
permanent owners do not need a write after every attack or encounter. Splitting
Combat from Expedition would require a cross-service state handoff after every
fight without an independently useful combat data owner.

## Decision

Create `backend/hero-association-lib` as a Maven aggregator with a pure Java
`combat-engine` module (`hero-association-combat-engine` artifact). The engine
accepts pinned inputs, combat time, and randomness, and returns updated state
and visual events. It does not access Redis, PostgreSQL, RabbitMQ, Quarkus, or
the browser.

Expedition is the service that owns the Manager's Party and active Map run.
It stores only a compact, current run snapshot in its own Redis keyspace:
Party and encounter identity, current Hero resources and progression, stamina,
carried assets, and lifecycle state. It does not retain a history of finished
fights or per-hit database rows. Workers use the combat-engine library to run
active fights server-side, even with no viewer. They schedule due actions
without one thread or one database poll per battle. A fight updates the run's
current Redis state when it ends and then stops. Only an authorized, explicit
Continue command starts another encounter. A wipe prevents Continue but does
not automatically return the Party; a return requested during a fight takes
effect after that fight.

BFF keeps the browser-facing authenticated WebSocket. It streams temporary
combat snapshots and events to viewers and accepts player intent, but never
calculates the outcome. Visual events do not go through the durable business
settlement path. Hero and Assets retain permanent data ownership; Expedition
does not send them XP, skills, stamina, or loot after each fight. On return,
Expedition atomically freezes the final Redis state and queues aggregated
settlement in Redis for publication to RabbitMQ. The receiving owners apply
the settlement idempotently by expedition ID. Expedition removes the active
Redis state only after safe handoff and required settlement confirmation.

Redis is authoritative for unbanked expedition progress, not a disposable
cache. Before cutover, configure persistence, no eviction of active runs,
recovery, and monitoring. Define active-fight restart behavior from a pinned
boundary snapshot and deterministic random seed. Service instances must not
depend on a singleton Java object for authoritative run state. The exact
failure policy and settlement acknowledgments must pass tests before cutover.

Core remains the live quest-battle writer until the Expedition path, BFF
socket, and frontend pass their cutover gates. Existing Combat-service sandbox
code remains non-live until the new path is proven; remove obsolete code and
deployment only after no supported workflow depends on it. The earlier
Combat-before-Market priority and BFF-owned browser boundary remain.

## Alternatives considered

- Keep Combat as a separate service: independently deployable, but adds a
  per-fight boundary, duplicate active state, and persistence infrastructure
  for data that belongs to the run.
- Keep every active fight solely in one JVM: avoids shared-store operations,
  but loses the run on restart and makes multi-replica ownership fragile.
- Persist each attack or fixed-interval battle snapshot to PostgreSQL: eases
  replay but scales writes with active combat time rather than meaningful
  lifecycle changes.

## Consequences and follow-up

The library can be tested and versioned independently while Expedition API
and worker deployments can scale without creating another domain boundary.
Redis loss can still lose unbanked progress; persistence and failure recovery
are explicit cutover requirements, not implied by calling Redis a cache.
Settlement is at least once across Redis and RabbitMQ, so consumers must be
idempotent. Later mid-fight player commands will need their own ordering and
recovery contract; they are not part of the first automatic-combat slice.

The [Expedition contract](../EXPEDITION_CONTRACT.md) records the admission,
versioned Redis state, and restart rules for the next implementation slice.

Run 100, 500, and 1,000 **concurrent fights** as separate load tiers, with
and without viewers. Record correctness and resource use before deciding
whether any additional split is justified. Follow the ordered
[Combat and Expedition plan](../COMBAT_EXPEDITION_PLAN.md).
