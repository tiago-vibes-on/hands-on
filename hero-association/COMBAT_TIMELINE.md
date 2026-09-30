# Expedition fight timeline

Status: non-interactive first slice implemented. Expedition is the
authority for combat; Map only animates its events. A fight continues without
a browser connection. Continue is still required before another encounter.

## Current slice

At fight start the worker deterministically simulates the pinned combat-engine
snapshot once, including its RNG seed, until the terminal outcome. It stores a
bounded, fight-ID-scoped plan in Expedition Redis: the terminal Hero state and
outcome, timestamped events with stable sequence numbers, and short visual
checkpoints. Redis is the run-time store; no attack or frame is written to the
Core database. A second worker must reuse the same plan, not resolve again.

The BFF WebSocket sends only the current authoritative visual state and recent
events, not the full future plan. Events are ordered by sequence number and
carry their combat-relative time. The browser interpolates and animates them;
it never decides damage, progression, loot, or the winner. Reconnect loads the
current state and skips already elapsed animations. A hidden tab or a stream
gap drops queued hit effects and paints the current server state immediately;
only subsequent live events animate. The plan is discarded after the run
leaves that fight; a new fight has a new ID and sequence space.

The current no-interaction fight may be calculated fully upfront. This is
bounded by the engine's 30-minute/100,000-event safety limits, and is useful
while interactions are not implemented. The resulting event stream is split
into five-second visual windows so reads do not replay the combat engine from
the beginning each second. Plan size, calculation time, and concurrent-start
CPU must be measured before the 100/500/1,000-combat load tests resume.

## Future mid-battle action

An interaction command will carry a UUIDv7 command ID, the fight ID, expected
version, and the requested action. Expedition will validate ownership,
resources, cooldown, and a server-defined effective time. It will branch from
the nearest authoritative checkpoint, invalidate only *unplayed future*
events, and publish a higher timeline revision. The frontend will cancel
queued events from the old revision and accept the replacement; it cannot
submit an outcome. Avoid sending a long future timeline to the browser,
because doing so leaks results and makes corrections visually confusing.

Before this action API is enabled, move from full precomputation to bounded
lookahead (for example five seconds) with persisted combat/RNG/progression
checkpoints. Each window will advance once; only a valid interaction will
recalculate future windows. A delayed or disconnected viewer must not pause
the server worker. This is a planned extension, not a current interaction
feature.

## Validation

Check deterministic replay, single-plan use across worker retries and replica
handoff, event ordering and deduplication, Hero/creature resource snapshots,
terminal settlement without viewers, reconnect without old hit popups, and
the old Phaser effects on Map. Load tests remain deferred until the player
combat path works end to end.
