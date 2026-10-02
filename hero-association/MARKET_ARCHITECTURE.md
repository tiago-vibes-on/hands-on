# Market service architecture

Market is an independent Quarkus service with its own PostgreSQL database and
credentials. BFF routes `/api/v1/market/**` to Market. Core resolves Manager identity and agency leadership. Assets owns catalogs,
wallets, inventories, reservations, closure and atomic settlement in its own
database. No service reads another service's tables. Core's old order writer is removed.

```text
Browser -> Envoy -> BFF -> Market -> Market PostgreSQL
                     |       |
                     |       +-> private Assets API -> Assets PostgreSQL -> Core permission API
                     +-> other Core and Expedition APIs
```

Envoy still limits placement attempts to five per second per validated subject.
BFF keeps OIDC tokens server-side and enforces CSRF. Market validates the
`hero-association-market` audience. Private Assets calls carry a dedicated service
credential; context and reservation creation additionally carry the player's
original token with Assets and Core audiences. Background recovery stores no player tokens.

## Durable lifecycle

Placement starts with a client-generated UUIDv7. Market persists the requester
subject, resolved Manager/owner/catalog snapshot, immutable request, and stable
order/reservation/closure IDs. A confirmed reservation creates the order in a
short local transaction and returns `201`. Unknown delivery returns `202` with
a placement reference. Exact retries cannot reserve twice. Status reads belong
to the initiating subject. The browser retains unresolved IDs across reloads.

A worker first reads Assets reservation status. It publishes a matching confirmed
reservation. If no reservation was confirmed, it records `PENDING_ABORT`, then
permanently closes the reservation key before marking `ABANDONED` or `REJECTED`.
A delayed reserve cannot escape that fence. A lease guard prevents an old worker
from publishing after another worker starts abandonment.

Each item has a Market book row locked only during local matching changes.
Priority is a per-item sequence assigned when an order becomes matchable.
Matching selects the best compatible price, then oldest priority, excludes the
same owner, and executes at the resting order's price. In one transaction it
records a `PENDING_SETTLEMENT` trade and allocates quantities on both orders.
Allocated quantity cannot match again. Core settles the stable trade ID once,
including the existing 10% seller fee and any buyer price-improvement refund.
Only a validated receipt advances the Market fills.

Cancellation verifies current ownership through Core and freezes the order as
`PENDING_CANCEL`. Allocated trades finish first. Closing the remainder refunds
only unfilled assets, and Market records `CANCELLED` after the closure receipt.
Repeated DELETE requests and worker delivery return the recorded result.

Workers claim records with a 60-second database lease and UUID claim token;
completion compares that token and state under a row lock. Individual Assets
calls have five-second deadlines; local transactions have 20-second timeouts.
No network call occurs while a Market transaction is open. Recovery runs every
second, alternates operation kinds, and retries with exponential delay from
one to 30 seconds plus jitter. Separate pods can recover the same queue safely.
Receipt mismatches or invariant conflicts quarantine affected records, retaining
allocated quantities for investigation. UUIDs and receipts are retained.

Metrics expose pending counts and oldest age by operation kind, recovery
attempts and failure categories. Alert on oldest pending age over five minutes.
Logs include operation IDs and categories, never credentials or JWTs.

## Delivery and reset

Compose, k3d, hybrid development, Jenkins service jobs, and six-image candidate
archives include Market. Its database uses separate credentials and a separate
PostgreSQL instance. Core, Assets and Market deterministic seeds share owner IDs and reservation keys;
wallet/inventory seeds already exclude reserved amounts. Before Flyway, reset
and seed all three databases with application writers stopped. An independent reset
of one database breaks that invariant. Keycloak users do not require a reset;
`sync-market-realm.mjs` adds the client and audience to an existing realm.

The private HTTP transport needs no new RabbitMQ queues. A later event outbox
or history API can use persisted trades without changing Assets ownership.
See [Assets recovery](ASSETS_RECOVERY.md), [SPEC](SPEC.md), and the
[Market module README](backend/hero-association-market/README.md).

The local cutover completed on 2026-10-02 using the five-image
`market-extraction-20261002-v3` archive. Its isolated gate passed 16 browser/API
cases, Core-cache fallback, BFF restart/outage/expiry recovery, and k6 thresholds.
The daily promotion passed nine browser cases, Expedition and Map settlement,
all running image digests, and k6 after the coordinated Core/Market reset.
The archive retains `k3d-e2e-verification.json` and `k3d-promotion.json` locally.
Frontend WSL hot reload was restored afterward, and the nine browser cases
passed again through its Envoy route.
