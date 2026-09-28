# ADR 0003: Market service boundary

- Date: 2026-09-28
- Status: Accepted route family; owner payload under review before extraction

## Context

The market currently lives inside Game Core and uses the same PostgreSQL
transaction as agency gold and inventory. The earlier agency-scoped mutation
URLs and agency-state responses made the public contract depend on that
implementation. Market will become a separate microservice, but moving order
matching and resource transfers into separate databases is not an atomic
rename or deployment change.

## Decision

Expose all market operations under one public route family:

- `GET /api/v1/market/orders` lists open orders.
- `POST /api/v1/market/orders` accepts `agencyId`, `side`, `itemId`,
  `quantity`, and `priceGoldPerItem`; it returns the created order and
  its status.
- `DELETE /api/v1/market/orders/{orderId}` derives the agency from the
  order and returns its cancelled status.

The current agency-only placement and cancellation require leadership of the
order's agency. The planned Manager-owned trading account needs a revised
owner selector and personal authorization rules before extraction.
In k3d, Envoy retains the five-placement-attempts-per-second limit per
authenticated user, regardless of agency. Normal local Traefik has no
limit. The frontend refreshes agency state separately after an order mutation. The old agency-scoped market URLs are removed while
the product is still in its resettable pre-production stage.

The target ownership is: Market owns the order book, matching, and trade
history; Core remains authoritative for agency membership, Manager and agency
wallets and inventories, and item definitions; BFF routes browser market calls
to Market. Market must
not access Core's database directly. The current implementation remains in
Core until a durable, idempotent reservation and settlement protocol is
designed and tested, including retries, partial fills, cancellation races,
and recovery from either service being unavailable. That protocol may use an
outbox and ledger, but this ADR does not choose it prematurely.

## Alternatives considered

- Keep agency-scoped URLs: workable, but unnecessarily couples the public
  market contract to agency-state routes and responses.
- Split the service immediately and share Core's database: faster to deploy,
  but it would create a distributed monolith and unclear data ownership.
- Split immediately with synchronous transfers only: failures between order
  matching and resource transfers could leave gold, inventory, and orders
  inconsistent without idempotency and recovery.

## Consequences and follow-up

The API and frontend no longer require Market to return a Core agency-state
snapshot. This prepares the external contract, not the internal transaction
boundary. Implement the separate Market service, its own data store, BFF
routing, and settlement protocol as the roadmap's next market architecture
task. Preserve the k3d Envoy market rate limit when routing changes.
The proposed order lifecycle and failure-recovery checks are in the
[Market service extraction plan](../MARKET_ARCHITECTURE.md).
