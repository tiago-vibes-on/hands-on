# Expedition

Expedition owns the Redis Map run and fight loop. The player and visual APIs
remain opt-in in ordinary development and are enabled in the k3d lab.
Core Quest combat is retired. Admission pins World Map/Creature versions and
an optional Quest assignment, alongside Core Hero baselines. Dungeons have
finite floors and pause after completion; Continue restarts the pinned first
floor in the same Expedition while retaining Hero resources/progression,
carried loot and Quest progress. The Map's optional auto-continue sends that
command after completion. Fields repeat encounters.

Never construct a `PreparedEntry` from browser input: Core reserves the
Party and supplies the trusted Hero baseline.

The worker restores each pinned opening snapshot, resolves a fight in memory
with the shared `combat-engine`, and commits only the terminal Hero/resource
state to Redis. It does not write per-hit SQL or RabbitMQ messages. A win waits
for explicit Continue; a wipe waits for Return; Return during a fight is
deferred until that fight ends. At Return, a frozen aggregate is queued
atomically with the run transition. RabbitMQ broker confirmation and Core
owner application are separate states; Redis is cleaned only after the
matching Core-applied acknowledgment. A private Core admission API and
Expedition-side entry coordinator now exist.
Each defeated Creature independently rolls its pinned gold, rune and item
entries with a separate deterministic RNG stream. Troll and Forest Wolf roll
50% for 1–25 gold, 1% for one of each of seven rune types, and 5% each for
1–5 Iron Ingots and 1–5 Magic Crystals. Drops can coexist and quantities are
uniform and inclusive. Carried loot updates with the terminal fight state and
is credited once on Return; repeated clears cap Quest progress and pay one
reward per assignment. The public view's `canContinue` governs dungeon repeats.
Core returns the authenticated Manager's pinned Hero baseline over a
service-key-protected internal call; Redis atomically fences abandoned IDs
before Core releases them. The disabled-by-default HTTP API validates an
Expedition-audience bearer token; Core maps that same token to the Manager
before every owned-run command. The BFF holds the token, and the browser
keeps only its opaque session cookie. HTTP command responses omit the
hidden fight RNG seed. The private visual API is also disabled by default;
when enabled, it requires a separate shared BFF service key and returns an
owner-checked Redis snapshot with the current state and recent events from
one fight-scoped timeline. A worker calculates the non-interactive fight once
and stores five-second event windows and a terminal outcome in Redis; visual
reads do not rerun the combat engine. It neither queries Core per frame nor
persists individual hits. Configure
`HERO_ASSOCIATION_EXPEDITION_VISUAL_API_ENABLED=true` in Expedition and the
same uncommitted 32-character-or-longer
`HERO_ASSOCIATION_EXPEDITION_BFF_SERVICE_KEY` in Expedition and BFF only for
integration work. The plan size and concurrent calculation cost must be measured in the planned
100/500/1,000-concurrent-fight tests.

The [isolated browser E2E lane](../../e2e/README.md) builds this
service from its multi-stage `Dockerfile` with the shared combat library and
runs an opt-in, disposable Core/Expedition/BFF/Map integration journey. From
`backend/`, start the optional private Redis and RabbitMQ dependencies
and run the tests:

```bash
docker compose -f compose.expedition.yaml up --detach redis-expedition rabbitmq-expedition-setup
./mvnw -pl hero-association-expedition -am clean test
```

The setup job creates the durable exchanges and queues and grants separate
service accounts only their required publish/read permissions. Core uses
`HERO_ASSOCIATION_CORE_SETTLEMENT_RABBITMQ_PASSWORD`; Expedition uses
`HERO_ASSOCIATION_EXPEDITION_WORKER_RABBITMQ_PASSWORD`. The original
`HERO_ASSOCIATION_EXPEDITION_RABBITMQ_PASSWORD` belongs only to the local
broker administrator and setup job. Defaults in `.env.example` are for
local development only.

Tests use disposable Redis and RabbitMQ containers automatically. For Quarkus dev mode,
install the shared library first, then start the service with its worker
enabled:

```bash
./mvnw -pl hero-association-lib/combat-engine,hero-association-lib/game-contracts -am install
cd hero-association-expedition
HERO_ASSOCIATION_EXPEDITION_WORKER_ENABLED=true ../mvnw quarkus:dev
```

The local Redis listens at `localhost:16381` and RabbitMQ at `localhost:15675`
by default. This isolated Redis
uses AOF `appendfsync always` and `noeviction`; do not use the BFF session or
Core Creature-cache Redis for active runs. Its Docker volume contains unbanked
state: do not remove it while a run may be active. The one-second scheduler
checks at most 64 due fights per pass; Redis leases and a state-version CAS
fence competing replicas. Scheduler entries are rebuildable from run snapshots.
The exact ruleset, RNG version, rates, and seed are pinned at each encounter.

`docker compose -f compose.expedition.yaml down` stops the dependencies
without removing their volumes. The settlement worker and Core consumer are
off by default. Do not enable a live flow before the authenticated Expedition and BFF routes,
worker reconciliation, and end-to-end checks are complete. To exercise the
private Core handoff, set the same random 32-character-or-longer
`HERO_ASSOCIATION_EXPEDITION_CORE_SERVICE_KEY` in Core and Expedition;
never commit its value. Set
`HERO_ASSOCIATION_EXPEDITION_ADMISSION_RECONCILER_ENABLED=true` only
after both services share that key and dedicated Expedition Redis is healthy. See the
[settlement handoff](../../EXPEDITION_SETTLEMENT.md),
[plan](../../COMBAT_EXPEDITION_PLAN.md) and
[run contract](../../EXPEDITION_CONTRACT.md).

Return aggregates use schema version 2 with `mapId`, `mapVersion` and nullable
`quest` progress. Core confirms Quest progress/reward, carried Assets and Hero
progress before acknowledging. Configure `HERO_ASSOCIATION_QUEST_BASE_URL` and
`HERO_ASSOCIATION_QUEST_EXPEDITION_SERVICE_KEY` for admission/release. See
[World and Quest contracts](../../WORLD_QUEST_ARCHITECTURE.md).
