# ADR 0007: Extract Combat before Market

- Date: 2026-09-29
- Status: Superseded by ADR 0008 for Combat and Expedition implementation

## Context

This record preserves the earlier standalone Combat-service decision. Follow
[ADR 0008](0008-combat-engine-in-expedition.md) and the current
[Combat and Expedition plan](../COMBAT_EXPEDITION_PLAN.md) for new work.

Game Core currently runs combat and market in one service. The frontend
watches an expanded fight through two-second combat sync polling, while a
background worker advances battles without viewers. The intended first live
combat channel is a browser WebSocket owned by BFF. Optional mid-battle
commands are planned later; the first release does not need item mechanics.

ADR 0004 keeps Core as a temporary modular monolith while boundaries are
defined. Its original "rewards before extraction" sequencing is no longer a
prerequisite for moving the already working combat engine. Market extraction
also needs a separate Assets reservation and settlement contract.

## Decision

After pinning Hero and Creature battle inputs and defining durable progression
and outcome contracts, extract Combat before Market. Combat will own active
battle state and its database. Quest, permanent Hero progression, Creature
definitions, and the current market remain in Core during this cutover.

The browser connects only to BFF. At the Combat cutover, BFF introduces the
public combat WebSocket and bridges it to Combat over a private authenticated
connection; Combat is not browser-facing. Start with authoritative snapshots
and ordered events, with a versioned command envelope ready for later optional
actions. Do not add a temporary WebSocket to Core or speculative future
simulation. Keep Core's current combat path authoritative until the new
service, BFF socket, frontend, and tests can switch together.

After that cutover, add Map and Expedition. Map owns reusable Field and
Dungeon definitions. Expedition owns one persistent Party per Manager and
its Map run; entering a Map does not require a Quest. Initially only personal
heroes can join a Map run. A return during battle waits for that battle, while
a wipe does not automatically return the Party. Add optional Quest objectives
later, then establish Assets reservation/settlement contracts in Core and
extract Market. This ADR revises the extraction sequence in ADR 0004;
Core's temporary role and ADR 0003 remain accepted.

## Alternatives considered

- Extract Market first: preserves the previous order but postpones the
  interactive Combat boundary and first browser WebSocket.
- Add a BFF WebSocket while combat still runs in Core: delivers the transport
  sooner but creates a temporary Core bridge and another cutover.
- Expose Combat directly to the browser: avoids a BFF relay but requires a
  separate browser authentication boundary and conflicts with the current
  BFF-owned session model.

## Consequences and follow-up

Combat extraction must solve reliable delivery and idempotent application of
battle results and progression in Core. It must not call other services for
each attack or send every visual hit through RabbitMQ. The live socket needs
session and Origin checks, authorized subscriptions, reconnect snapshots,
bounded replay, slow-client handling, and multi-replica tests. Existing Core
polling remains until the coordinated cutover. Market continues to work in
Core with its existing k3d gateway rate limit while Assets and Market
extraction are prepared.

Track the exact ordered tasks in the
[service extraction plan](../SERVICE_EXTRACTION.md).
