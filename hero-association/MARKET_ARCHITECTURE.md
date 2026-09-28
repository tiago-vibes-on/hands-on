# Market service extraction plan

Status: design proposal. No separate Market service or RabbitMQ integration has
been implemented yet. [ADR 0003](adr/0003-market-service-boundary.md) records
the current market route family and original agency-only contract. The new
[game ownership design](GAME.md#planned-personal-and-agency-ownership) also
allows Manager-owned trades, so the order-owner payload and authorization
rules must be revised before extraction. This document describes the target
flow without losing gold, items, or orders.

## Current and target architecture

Today, Game Core owns market orders, agency gold, and inventory in one
PostgreSQL database and updates them in one transaction. Manager-owned gold,
heroes, and inventory are planned but not yet implemented. The BFF already
exposes `GET/POST /api/v1/market/orders` and
`DELETE /api/v1/market/orders/{orderId}`. Its Redis-backed limit allows five
order-placement attempts per second per authenticated user, across sessions
and BFF replicas. Envoy Gateway is the k3d ingress, not the per-user limiter.

The proposed service boundary is:

```text
Browser -> Envoy Gateway (k3d) or Traefik (local) -> BFF
                                                     |-> Core -> core database
                                                     `-> Market -> market database
                                                                  |
                                                                  `-> private Core operations
```

Core will own agency membership, personal and agency wallets, heroes, item
definitions, inventories, and their reservations and transfers. Market will
own orders, matching, trade history, and order status. Market must never read
or write Core tables directly. Initially,
the two databases can share one PostgreSQL server/container, but use separate
databases and credentials. Neither service should have access to the other's
database. Separate PostgreSQL instances can be considered later without
changing these ownership rules.

Market is private: the browser still calls the BFF. BFF authenticates the
browser, enforces CSRF and the existing per-user placement limit, and routes
market paths to Market. Core must authorize personal orders against the
requesting Manager and agency orders against a leader or explicit trading
permission. The current `agencyId`-only placement payload cannot express
both owners; a revised owner selector and its authorization rules are needed
before extraction. Browser-supplied owner IDs are not proof of ownership.
The exact service-to-service authentication mechanism still needs to be
specified.

## Order lifecycle

The names below describe proposed behavior, not implemented states. A
**placement intent** is an internal recovery record, not a created or
matchable market order. IDs for placement intents, reservations, orders, and
trades must be stable and unique so retries refer to the same operation.

### Place an order

1. BFF admits `POST /api/v1/market/orders` under the user's shared rate limit
   and forwards it to Market.
2. Market durably records a `PENDING_RESERVATION` placement intent with the
   trading owner (Manager or agency), requester, side, item, quantity, price,
   and reservation ID. It does **not** create an open order or expose one in
   the order book.
3. Market asks Core to reserve the maximum required gold for a BUY order or
   the required item quantity for a SELL order. This is a command, not an
   `OrderCreated` notification. In **one Core transaction**, Core verifies
   personal ownership or agency trading permission, locks the selected owner's
   balance or inventory, checks that enough is available, and reserves it.
   A separate balance check followed by a later reserve would be unsafe:
   another order could spend the balance between those calls. Core records
   the reservation ID idempotently.
4. Only after Core confirms does Market create an `OPEN` order and make it
   matchable. If Core rejects the reservation, Market marks the intent
   rejected, returns the appropriate error, and creates no order. A timeout
   leaves only the intent pending and triggers a retry with the same ID.
   If Core reserved resources before Market crashed, that retry must return
   the original result, not reserve them again. An unrecoverable placement
   must eventually release its reservation.

With synchronous Core calls, the normal API response can be the created order.
If confirmation is still unknown at the deadline, the API must return a
pending **placement** reference (for example `202 Accepted`), not claim an
order was created. The UI would show that placement as in progress and query
its outcome. The exact response and polling contract still need a decision.

### Match and settle a trade

1. Market chooses a match using the existing price-time rule and records a
   trade with a unique trade ID as `PENDING_SETTLEMENT`. It prevents the
   participating quantity from matching a second time while settlement is
   pending.
2. Market asks Core to **settle** that trade. Core atomically transfers the
   reserved gold and items between the two trading owners, applies the 10%
   market fee and any buyer price-improvement refund, and records the trade ID
   as completed. A repeat request with that ID returns the same outcome
   without paying twice.
3. After Core confirms, Market commits the corresponding fills and remaining
   quantities. If confirmation is lost, Market retries the same trade ID.
   Partial fills leave only the unfilled quantity available for new matches.

The exact fee calculation and settlement payload must be specified together
so Core can validate values against the reservations rather than blindly
trusting a transfer amount supplied by Market.

### Cancel an order

Market first stops new matching for that order. It must resolve any pending
settlement, then asks Core to **release** only the still-unfilled reservation.
After Core confirms the idempotent release, Market marks the order `CANCELLED`.
A failure leaves a durable `PENDING_CANCEL` operation for retry; it must not
silently report success while resources remain reserved.

## Delivery choice: private API or RabbitMQ

The recommended first implementation is private, authenticated Core API calls
plus durable pending operations and retries in Market. RabbitMQ is a viable
later transport for reservation and settlement commands or for publishing
events such as `TradeSettled` to feed, notifications, and analytics. It is not
required merely because Market is a separate service.

If commands use RabbitMQ, Market must persist each pending operation
(including a placement intent) and an outbox record in one local transaction.
A publisher sends the command. Core processes it idempotently and stores both
the result and its reply outbox record in one Core transaction. Market then
consumes the reply with deduplication. Broker acknowledgement alone is not a
distributed transaction: messages can be retried after a crash. The choice
between private API and RabbitMQ, and any response-time expectations, remain
open decisions.

## Required safeguards and checks

- Define order, reservation, trade, and cancellation state machines, including
  which states are visible and which quantities are matchable.
- Give Core a reservation/settlement ledger with idempotency keys and strict
  checks that gold or item quantities cannot become negative or be transferred
  twice. Keep retries and reconciliation until pending operations resolve.
- Authenticate Market-to-Core calls and preserve the initiating user identity
  for personal ownership and agency permission checks; do not trust
  browser-supplied headers or owner IDs.
- Make the BFF route market paths to Market while retaining its existing
  five-per-user-per-second placement limit. Do not expose Market directly to
  the browser.
- Add separate Market build, configuration, database bootstrap/seed, Compose
  and k3d deployments, health checks, traces, metrics, and logs. Track stuck
  pending operations, retry counts, and reservation/settlement failures.
- Test accepted and rejected reservations, partial fills, fee/refund math,
  cancellation races, duplicate commands, lost responses, Core/Market/broker
  outages, restarts, and eventual reconciliation. Keep browser E2E and k6
  coverage for the public route and rate limit.

Do not extract Market with the current agency-only order payload. First define
how the request selects a personal or agency trading account and how Core
validates that choice; see [ROADMAP.md](ROADMAP.md).

Local and pre-production data can be reset and reseeded while the project has
no Flyway migrations. Once Flyway is introduced, service database changes
must use explicit migrations that preserve data.
