# Combat and Expedition implementation plan

Status: player-facing Map cutover complete in k3d; load tests deferred. The
shared combat library, Expedition encounter loop, and aggregate settlement
handoff are live. Ordinary local development still keeps Map opt-in. Follow
[ADR 0008](adr/0008-combat-engine-in-expedition.md). Core Quest combat
still runs in Core. The standalone Combat-service sandbox and its Core fact
inbox have been retired; [SPEC.md](SPEC.md) documents the remaining runtime.

The current fight-timeline design is recorded in
[COMBAT_TIMELINE.md](COMBAT_TIMELINE.md).

This is the bounded sequence for subsequent `next` requests. Work only on
Combat and Expedition and their required BFF, frontend, Core data-owner,
Redis, RabbitMQ, local, and k3d integration. Do not move on to other domains
after the final load-test report without a new explicit request.

## Target behavior and ownership

- `hero-association-lib` is a Maven aggregator; its `combat-engine` module is
  a plain Java library with no service or infrastructure dependencies.
- Expedition owns one active Map run per Manager and its compact current state
  in a dedicated Redis keyspace. The first encounter may use a fixed seeded
  Troll; a separate Map-service extraction is not part of this sequence.
- A server worker runs each fight without browser input and stops at its
  terminal outcome. No automatic next fight: the Manager must send Continue.
  A wipe leaves the Party on the Map until explicit return. Return during a
  fight waits for that fight to finish.
- A finished fight changes only the current expedition state. Store enough
  current Hero resources, XP and skill progress, stamina, carried assets,
  encounter position, and lifecycle status for the next fight or return; drop
  finished fight history. No per-attack or fixed-interval SQL writes and no
  per-fight permanent Hero/Assets settlement.
- At agency return, freeze and publish one aggregated settlement through a
  Redis-backed pending queue to RabbitMQ. Core initially remains the owner of
  permanent Hero and Assets data. Owners apply the settlement once by
  expedition ID. Keep the Redis snapshot until required settlement is safe;
  then remove it. Distinguish broker confirmation from owner application.
- BFF owns the browser WebSocket and authorization. It streams temporary
  authoritative visual state only to subscribers; no browser-calculated
  results or durable per-hit messages.

## Ordered work

1. [x] Record the new architecture and supersede the standalone Combat
   cutover plan without changing the live path. Mark the earlier sandbox
   superseded in the repository roadmap and architecture records.
2. [x] Create `backend/hero-association-lib/combat-engine` and move the pure
   deterministic rules out of duplicated Core/Combat packages. Provide a
   documented clean Maven build for Core and the library, retain engine tests,
   and remove the parity script only when no duplicate engine remains. Do not
   migrate live battle ownership in this step.
3. [x] Define Expedition's minimal run contract and Redis state schema:
   UUIDv7 identity, Party access, versioned pinned inputs, one run per Manager,
   lifecycle states, atomic version checks, non-evictable active keys, and
   restart behavior. Load persistent Hero/Assets baselines once at entry. See
   [the Expedition contract](EXPEDITION_CONTRACT.md).
4. [x] Implement the server-run encounter loop using the library: no viewer
   required, no per-fight thread or database polling, bounded due-action
   scheduling, current state updated at fight boundaries, explicit Continue,
   wipe, return between fights, and deferred return during a fight. Verify
   duplicate commands and multi-worker ownership.
5. [x] Implement aggregate settlement on return. Atomically freeze final
   Redis state and queue it for retry; publish through RabbitMQ with routing
   and broker confirmation; apply it idempotently to Core-owned Hero/Assets;
   reconcile failures before marking the run returned and removing Redis
   state. Verify no XP, skill, stamina, or carried asset is lost or applied
   twice during retries and restarts. The private component tests cover retry,
   rebuild, broker routing, Core idempotency, and owner-ack cleanup; a real
   two-service restart drill remains part of step 7.
6. [x] Wire the private Core admission reservation to Expedition entry,
   reconcile orphan reservations, then add authenticated BFF WebSocket and
   frontend Map/Expedition flow. Stream only subscribed fights, reconnect
   from a current snapshot, validate session and Origin, and exercise
   explicit Continue. The internal Core admission API, Expedition client,
   Redis cancellation fence, and disabled-by-default orphan scanner are in
   place. Owner-scoped Expedition HTTP commands and BFF HTTP routing
   now exist, but the API is disabled by default. The BFF has a dormant,
   session- and Origin-checked WebSocket that sends an owner-checked snapshot
   on connect/reconnect. A local scheduler pushes changed fight visuals from
   Expedition's private Redis-only API to subscribed sockets without per-frame
   Core reads. A feature-flagged frontend Map page now loads the active run,
   enters the fixed Troll Field with a prepared personal-hero Party, renders
   socket visuals for three Trolls, and sends Continue/Return commands. An
   opt-in Map toggle can send Continue after a victory while the page is open;
   the server never advances an encounter without that command. The feature
   flags remain off in ordinary local development. The isolated local journey passed
   entry, live/reconnected visuals, Continue, deferred Return, and Core
   settlement. Step 7 verified k3d restart recovery and routine promotion.
   The frontend Quest board now shows information and progress without the
   old expanded combat view or its two-second sync polling.
7. [x] Validate the local and k3d Map journey and promote the player-facing
   path. A paused Redis run kept its phase and state version across a Core
   rolling update and an Expedition restart; authenticated API and visible
   Map journeys then passed through settlement. A four-image archive-backed
   pipeline and an Expedition-only service promotion both passed isolated
   browser, k3d browser, Expedition, Map, and market gates. The standalone
   Combat sandbox is removed. Core Quest combat remains intentionally live:
   Quest start still creates a persisted `QuestCombat` for the Core worker.
   Retire that Quest worker/sync path only with a separate Quest redesign.
   Runtime commands are in the backend and k3d runbooks.
8. [ ] Add and run repeatable load tests for **100, 500, and 1,000 concurrent
   fights**, not merely 100/500/1,000 requests. Exercise fights with no
   viewers and with WebSocket viewers, a disconnected browser, Continue, and
   agency return. Record fight completion and settlement correctness,
   duplicate/missing outcomes, event delay, CPU, memory, Redis operations,
   combat-path SQL traffic, and generator saturation. Compare tiers under
   fixed resources before changing scale. Report measured bottlenecks and
   tune only those; a completed run is not by itself a production capacity
   claim. Deferred at the user's request until the player-facing combat flow
   and its normal promotion path are working reliably.

## Stop condition

The player-facing Map cutover is complete. Do not start step 8 until the user
explicitly resumes load testing. A later bare `next` does not authorize work
outside this plan.
