# Pre-cutover Combat-service sandbox contract

Status: historical sandbox contract. Its standalone service and per-battle
persistence are superseded for new work by [ADR 0008](adr/0008-combat-engine-in-expedition.md)
and the [Combat and Expedition plan](COMBAT_EXPEDITION_PLAN.md). The endpoints
and tables below describe existing isolated code, not the target live path.

Sandbox status: pre-cutover sandbox. Combat has protected private
start and advance endpoints, opt-in advancement/outbox workers, and the shared deterministic
`hero-association-lib/combat-engine` library. It can publish sandbox fact batches to RabbitMQ. Core has opt-in
durable intake and a manual, registration-gated Hero applier with no live
caller. The browser has no Combat stream. Core remains the only live battle
writer.
See [the extraction plan](SERVICE_EXTRACTION.md) and
[ADR 0007](adr/0007-combat-before-market-extraction.md).

## Ownership

- Combat owns authoritative active battle state, timers, temporary resources,
  ordered battle events, and the terminal battle outcome.
- Expedition owns the Manager's persistent Party and Map run. It selects an
  encounter and requests a battle; a Quest is not required. Until cutover,
  Core's existing Quest flow is the encounter coordinator.
- Hero owns permanent health/mana state, stamina, XP, skills, and loadouts.
  Creature owns versioned creature definitions. Combat receives pinned inputs
  from their owners; it does not query either domain per attack.
- BFF authenticates the browser WebSocket, authorizes subscriptions, and
  relays snapshots/events and later optional commands. It never calculates
  damage or advances timers.

## Start a battle

The private `POST /internal/v1/battles` start endpoint stores a `PREPARED`
record idempotently by UUIDv7 `battleId`. Its payload identifies the source
run (`QUEST_CORE` temporarily or `EXPEDITION`), `runId`, `partyId`,
`ownerManagerId`, the provisional `core-v1` ruleset label, and a typed
`CombatBattleSnapshot`. Combat validates the opening status and time,
nonempty living formations, unique UUIDv7 combatant IDs, engine stats, and
initial basic/spell timers before persisting a canonical snapshot. The
snapshot is stored separately from the immutable start request so later
engine progression can update it. An identical typed retry returns `200`;
a different payload under the same ID returns `409`. Initial creation
returns `201`. The endpoint requires a JWT for the
`hero-association-combat` audience with the `combat:start` role. The
Keycloak grant and Core caller are not wired yet, so this endpoint is not
part of the live quest flow. The caller must authorize the Manager and lock
Party availability before sending it.

The ordered `heroInputs` and `creatureInputs` arrays must match the Hero and
Creature snapshot formations one-for-one by UUIDv7 combatant ID. Hero inputs
carry the persistent Hero ID, class, levels, starting stamina, and equipped
rune slots; Creature inputs carry definition ID/version and base XP. Snapshot
stats and initial timers remain pinned separately. Rune critical bonuses must
match the snapshot's effective critical stats. Mage spells below the required
Magic level are listed as known but have no scheduled cast timer.

The engine snapshot pins formation, current/max health and mana, attack
damage and interval, basic-attack mana cost, recovery, effective critical
bonuses, known spells, and opening timers. Each equipped rune pins its ID,
slot, code, effect, and value. The start request preserves these inputs
unchanged; later balance-data changes cannot rewrite an active battle.

New Core quest battles now persist these Hero and Creature inputs in their
combat snapshots. The pre-existing seeded battle retains its own stats and
pinned rune rows. Core and Combat consume the same pure engine library; its unit tests live
with the library, and no mirrored production engine or parity script remains. Spell formulas and
the engine ruleset are still compiled code.
Before Combat cutover, introduce explicit ruleset versioning or drain active
battles before a rules-changing deployment. Do not describe the current
snapshot as protecting active fights from code-level formula changes.

## Progression and completion

The private `POST /internal/v1/battles/{battleId}/advance` endpoint accepts an
absolute battle time in `targetTimeMilliseconds` (at most 10 seconds ahead)
and requires `combat:advance`. A pessimistic battle lock serializes concurrent
advances. The new engine snapshot, sequence-numbered `battle_event` rows, and
`battle_progression_batch` outbox row commit together. Repeating the current
target time is a no-op; backward time or further advancement after completion
returns `409`. Manual and scheduled advances use the same locked transaction.
The scheduled worker runs every second only when
`HERO_ASSOCIATION_COMBAT_WORKER_ENABLED=true`; it is off by default. Its
persisted `last_advanced_at` anchor is initialized on battle start. Each
transaction catches up by at most ten seconds of battle time when the
database is retained. The disposable dev/Compose schema reset intentionally
discards battles on startup. A second replica rechecks the anchor after
taking the row lock and cannot replay the same interval. Completion stops
future worker advances.

Each UUIDv7 batch identifies its battle, inclusive fact-sequence range, and
chronological facts for active-battle stamina, qualifying Hero actions and
mana spent, creature kills with base XP and living Hero recipients, Hero falls,
and terminal outcome with final resources. Hero remains responsible for
stamina-dependent XP and skill formulas. UI hit events are stored separately.
The outbox worker is off by default. With
`HERO_ASSOCIATION_COMBAT_OUTBOX_ENABLED=true`, it selects the earliest
unpublished batch per battle with `FOR UPDATE SKIP LOCKED`, sends one
versioned JSON envelope through RabbitMQ, waits for a broker confirm, and
sets `published_at` in the same database transaction. The durable direct
exchange is `hero-association.combat.progression.v1`, routing key
`core.progression`, and durable queue
`hero-association.core.combat-progression.v1`. Each persistent message has
the batch UUID as AMQP message ID and includes `schemaVersion: 1`, battle
UUID, inclusive sequence range, creation time, and ordered facts. Failure
keeps the outbox row pending. A crash after broker confirmation but before
database commit can resend the same batch, so delivery is at least once,
not exactly once. Core's opt-in consumer validates schema version, UUIDv7
identities, AMQP message ID, size, fact types, and contiguous sequences before
storing the raw envelope in its singular `combat_progression_inbox` table.
A repeated batch ID with identical bytes is acknowledged without another row.
Conflicting payloads and invalid envelopes are requeued and logged for manual
resolution. Gaps and out-of-order batches are stored with null `applied_at`.
A separate manual Core applier requires an explicit
`combat_battle_registration` containing the Party and pinned Hero/combatant
mapping. Registration rejects a Party attached to a live Core Quest. The
applier row-locks its per-battle cursor and applies only the next contiguous
batch. Hero stamina, class skills, XP, and terminal health/mana commit with
`applied_at` and the cursor; a retry cannot double-count them. No runtime
caller creates registrations or invokes the applier yet. Quest/Expedition
outcomes remain a separate owner's responsibility. RabbitMQ is acknowledged
after the intake transaction commits, not after Hero application. The
consumer is disabled by default. Core's disposable dev schema reset discards
inbox and registration rows; the isolated RabbitMQ volume can retain older
sandbox messages.
A separate Expedition consumer will update run state: a party wipe stops new
encounters but does not return the Party. A return requested during battle
takes effect when that battle finishes.

Do not send every visual hit through RabbitMQ. BFF receives an authoritative
snapshot and a private ordered event stream from Combat, then relays it to
authorized browser sockets. Reconnect starts with a current snapshot and
replays retained events where possible. Combat continues without viewers.
Session/Origin checks, command IDs, acknowledgments, ping/pong, slow-client
handling, and multi-replica routing remain required before cutover.

## Cutover gate

Keep Core as the sole live battle writer until the Combat service owns its
isolated database and the private start/stream/fact contracts pass contract,
integration, reconnect, authorization, failure-recovery, browser, and k3d
checks. Switch one authoritative writer at a time; only then remove Core's
battle worker and polling/sync path.
