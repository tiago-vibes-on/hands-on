# Hero Association Combat (pre-cutover)

This independently buildable Quarkus service is the first Combat extraction
slice. It owns an isolated `battle` table in its own PostgreSQL database.
`POST /internal/v1/battles` stores a start request with a UUIDv7
`battleId`, a typed `CombatBattleSnapshot`, and ordered `heroInputs` and
`creatureInputs`. These pin Hero identity, class, levels, stamina and rune
slots plus Creature definition/version and base XP. They must align with the
snapshot formation. The service restores the snapshot through the engine,
validates opening time, living combatants, UUIDv7 identities, stats, timers,
and pinned inputs, then persists canonical state separately from the
idempotency payload. For now it accepts only the provisional `core-v1`
ruleset label. An identical retry returns the existing record; a different
payload under the same ID returns `409`. The record starts as `PREPARED`.

Sandbox-only `POST /internal/v1/battles/{battleId}/advance` accepts JSON such
as `{ "targetTimeMilliseconds": 1000 }`. This is absolute battle time, with
a maximum 10-second step. A battle lock serializes advances. Snapshot state,
sequenced `battle_event` rows, and a `battle_progression_batch` outbox row
commit in one transaction. Retrying the current target is a no-op; moving
backward or advancing after completion returns `409`. Batches contain
chronological stamina, action, mana, kill, fall, and terminal facts; they are
queued in PostgreSQL and can be published to the isolated RabbitMQ sandbox.
Core can receive them into an isolated inbox only when explicitly enabled.
Its manual, registration-gated Hero applier has no live caller yet.

An opt-in worker checks due battles every second. Its persisted
`last_advanced_at` clock allows bounded catch-up when the database is kept;
each transaction advances at most ten seconds through the same row-locked
path as manual advancement. It stops at a terminal outcome. The worker is
off by default. This disposable dev/Compose sandbox drops and recreates its
schema on startup, so those restarts intentionally discard battles.

An independent outbox worker is off by default. When enabled, it publishes
versioned batch envelopes to a durable RabbitMQ queue, waits for publisher
confirmation, then marks each row published. Failure leaves the row pending
for a later tick. Broker confirmation followed by a database failure may
cause duplicate delivery with the same batch ID; Core's inbox deduplicates
identical batches. The publisher keeps each battle's batches in sequence.
Core intake is also opt-in. A manual Core applier can process registered
Hero progression batches, but no live battle is registered or routed yet.
Do not enable either worker for the normal Core/BFF workflow.

The start endpoint requires a bearer token for the
`hero-association-combat` audience with the `combat:start` role; manual
advancement requires `combat:advance`. The Keycloak realm has no internal
caller grant yet. Combat and Core use the same `hero-association-lib/combat-engine` JAR, but only Core executes live
fights. Combat has no BFF stream, and neither Core nor BFF calls these
endpoints. Do not send browser credentials directly to Combat.

The engine has one source and one unit-test suite in
`backend/hero-association-lib/combat-engine`. For a clean build, run
`./mvnw -pl hero-association-combat -am test` from `backend/`. To use
standalone `mvn` or `quarkus:dev` here, first install the library from
`backend/` with `./mvnw -pl hero-association-lib -am install`. Then,
with Maven and Docker available, run from this directory:

```bash
mvn test
```

Quarkus Dev Services starts temporary PostgreSQL for tests, and the RabbitMQ
publisher test starts a temporary broker with Testcontainers. To run the
sandbox in dev mode, start its database and broker from `backend/`, then run
Combat on the host:

```bash
docker compose -f compose.combat.yaml up --detach postgres-combat rabbitmq-combat
cd hero-association-combat
mvn quarkus:dev
```

To opt into the sandbox worker for a single dev run, replace
`mvn quarkus:dev` above with
`HERO_ASSOCIATION_COMBAT_WORKER_ENABLED=true mvn quarkus:dev`.
To publish sandbox batches, set
`HERO_ASSOCIATION_COMBAT_OUTBOX_ENABLED=true` for the Combat process as well.
For the packaged sandbox with both workers enabled, run from `backend/`:

```bash
HERO_ASSOCIATION_COMBAT_WORKER_ENABLED=true HERO_ASSOCIATION_COMBAT_OUTBOX_ENABLED=true docker compose -f compose.combat.yaml up --build
```

Do not enable these workers for the normal Core/BFF workflow. There is no
internal Keycloak caller grant or live battle-start connection yet; enabling
the worker alone does not create a battle.

Combat listens at `http://localhost:17082`; its PostgreSQL port is
`15432`. The isolated RabbitMQ AMQP port is `15673`, and its management UI
is at `http://localhost:15674` with the local-only
`hero_association_combat` credentials (or the password set in `.env`).
The optional packaged sandbox is
`docker compose -f compose.combat.yaml up --build` from `backend/`.
It uses local-only credentials and drops/recreates its disposable schema;
the RabbitMQ volume can retain older sandbox messages across that reset.
It does not affect the default Core/BFF development stack. The health
endpoint is available at `/q/health/ready`; the private start endpoint
cannot be called with the current browser login tokens.

See [the Combat contract](../../COMBAT_CONTRACT.md) and
[the extraction tracker](../../SERVICE_EXTRACTION.md) for the remaining
work and cutover requirements.
