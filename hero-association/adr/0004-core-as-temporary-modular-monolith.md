# ADR 0004: Keep Game Core temporarily while defining domain boundaries

- Date: 2026-09-28
- Status: Accepted architectural direction; domain extraction is not implemented

## Context

Game Core currently implements agency, hero, quest, feed, and market behavior
against one PostgreSQL database. The game is still defining personal and
agency ownership of heroes, wallets, inventories, parties, and rewards.
Splitting services before those rules work together would introduce
cross-service transactions and failure recovery while the rules are changing.

## Decision

Keep Game Core as a **temporary modular monolith** while we implement and test
the game rules. It is not the intended permanent home for every game domain.
Develop new behavior with clear internal domain ownership and explicit
interfaces between domains, even when one Core transaction currently spans
them. Do not introduce direct database dependencies between future services.

First make Manager-owned and agency-owned assets, permissions, party and quest
flows, rewards, and market operations correct inside Core. Then extract
cohesive domains into independently buildable and deployable services when
their ownership, API contracts, and cross-domain failure handling are clear.
Market is a planned candidate; its target boundary and reservation/settlement
requirements are in [ADR 0003](0003-market-service-boundary.md) and the
[Market extraction plan](../MARKET_ARCHITECTURE.md). Other domains are not
assigned service boundaries yet. The BFF remains the browser-facing boundary.

## Alternatives considered

- Split domains immediately: establishes deployment boundaries early, but
  complicates still-changing ownership rules with distributed coordination.
- Keep all game domains in Core permanently: simpler operations, but does not
  match the planned domain-oriented service architecture.

## Consequences and follow-up

Core can use one database transaction to establish the new ownership model and
validate its behavior. Extraction remains real work, not a rename: each new
service will need its own data ownership, authorization, reliable
cross-service operations, deployment, and tests. Local and pre-production
data may be reset and reseeded until Flyway is introduced; this does not
remove the need to design safe runtime transactions.

Before implementing personal ownership, resolve the open product rules in
[GAME.md](../GAME.md) and [Milestone 12](../ROADMAP.md#milestone-12--personal-progression-and-shared-agencies).
Do not start Market extraction merely because its public routes are already
market-centric.
