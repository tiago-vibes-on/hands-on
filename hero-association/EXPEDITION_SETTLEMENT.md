# Expedition settlement handoff

Status: implemented in the local six-service stack. The opt-in Map entry calls
Core admission and accepts its pinned Hero baseline. Core owns Hero eligibility
and progression; Assets owns loadouts and carried resource credit.

## Ownership and delivery

1. Core's `ExpeditionAdmissionService.reserve` locks the Party and its personal
   Heroes, verifies Manager membership and availability, applies agency
   recovery, changes their activity to `ON_EXPEDITION`, and records a provisional
   reservation under the UUIDv7 Expedition ID. Outside that transaction it obtains
   an immutable Assets loadout receipt, then confirms the pinned baseline. A repeat with the same IDs returns
   the baseline; a settled ID cannot be reused. Only the opt-in Expedition entry calls it.
2. At Return, Expedition's Redis command or terminal-fight script atomically
   changes the run to `SETTLEMENT_PENDING` and writes a no-TTL, Manager-sharded
   pending marker. The run is frozen: no Continue or further Hero progress is
   accepted. A derived due-time index is rebuilt from pending markers after a
   worker restart.
3. The settlement worker serializes one canonical aggregate for the whole run
   (Hero XP, four skill totals, health, mana, stamina, carried gold, items,
   runes), publishes it with the Expedition ID as AMQP message ID to the
   durable `hero-association.core.expedition-settlement.v1` queue, and records
   broker confirmation in Redis. It retries until owner application is
   acknowledged. A broker confirmation is **not** an owner acknowledgment.
4. Core validates the entire aggregate against the unchanged reservation
   baseline before staging an asset workflow. Assets credits carried gold, items
   and runes atomically with a stable command receipt. Core validates that receipt,
   then applies Hero progression and stores the payload digest and `appliedAt`
   together with workflow completion in one Core transaction. Uncertain delivery
   remains pending. Exact retries cannot credit Assets or Hero XP twice; conflicting
   bytes are rejected.
5. Only after that transaction commits does Core publish a separate durable
   `hero-association.expedition.settlement-ack.v1` event. If this publish or
   the original message acknowledgment fails, RabbitMQ redelivers the original
   settlement: Core returns its existing receipt and republishes the owner
   acknowledgment. Expedition verifies the acknowledgment's ID and digest
   against the frozen aggregate and its recorded broker confirmation. Only
   then does one Redis script remove the run, active index, pending marker, and
   publication marker and write a bounded closure receipt.

The source Redis run remains authoritative while any handoff is unresolved.
No per-hit, per-fight, or fixed-interval SQL update is introduced. Carried
gold/items/runes are provisionally supported by settlement even though the
current fixed Troll encounter produces no loot. The agency's share of future
Map gold still needs a defined accounting rule before enabling gold drops.

## Local verification

`backend/compose.expedition.yaml` starts a dedicated AOF/noeviction Redis and
RabbitMQ for this private path. The broker is at localhost port `15675` and
its management UI at `15676`; both ports can be overridden in `.env`. The
default local broker credentials are deliberately weak and **not** production
credentials. Both settlement consumers are disabled by default. Do not enable
them for a live player flow merely by changing those flags.

The one-time `rabbitmq-expedition-setup` job creates the two durable exchanges,
queues, and bindings before consumers start. Runtime Core and Expedition use
distinct accounts with no configure permission; each can write only its own
exchange and read only its own queue. Run from `backend/`:

```bash
docker compose -f compose.expedition.yaml up --detach redis-expedition rabbitmq-expedition-setup
```

The broker administrator password is
`HERO_ASSOCIATION_EXPEDITION_RABBITMQ_PASSWORD`; the two runtime passwords
are `HERO_ASSOCIATION_CORE_SETTLEMENT_RABBITMQ_PASSWORD` and
`HERO_ASSOCIATION_EXPEDITION_WORKER_RABBITMQ_PASSWORD`.

From `backend/`, run the independent component suites:

```bash
./mvnw -pl hero-association-expedition -am clean test
./mvnw -pl hero-association-core -am test
```

Tests cover Redis freeze/cleanup, due-index repair, publish failure/retry,
matching and duplicate owner acknowledgments, RabbitMQ routing/confirmation,
Core reservation, unchanged-baseline enforcement, and exact-once Hero/Assets
application. The isolated browser flow now passes; the six-service archive gate verifies the k3d path. Core workflow tests cover
lost Assets receipts and acknowledgement gating.

Before cutover, validate the k3d path and full cross-service failure/restart
drill, then switch player traffic deliberately. Local Compose and isolated
E2E broker permissions are already separated. Track the remaining work in
the [implementation plan](COMBAT_EXPEDITION_PLAN.md).
