# Hero Association Market

An independently runnable Quarkus service for order placement, matching,
trade records, cancellation, and durable recovery. It stores only its own
records in `hero_association_market`; Assets controls all gold and items.

From `hero-association/backend`, build or test:

```bash
./mvnw -pl hero-association-market -am package
./mvnw -pl hero-association-market -am test
```

Production uses port 8084; development uses 17084. Set the datasource URL,
username and password to the separate Market database, `HERO_ASSOCIATION_ASSETS_BASE_URL`,
OIDC issuer/auth-server URL, and `HERO_ASSOCIATION_ASSETS_MARKET_SERVICE_KEY`.
The last value must match Assets and contain at least 32 characters. Player
access tokens require the `hero-association-market` audience; Keycloak's BFF
client includes Market, Assets and Core audiences. Never configure Core database
credentials here. Production validates an existing schema.

Use the main backend Compose stack or `deploy/k3d/hybrid.sh run market` for
local dependencies. Hybrid mode validates existing data and disables Dev
Services. Install the shared pure UUID library before independent dev mode:
`./mvnw -pl hero-association-lib/combat-engine -am install`, then run
`../mvnw quarkus:dev` inside this module with its environment configured.
The shared library has no Core entities or repository dependencies.

POST `/api/v1/market/orders` requires UUIDv7 `placementId`, ownerType,
side, itemId, positive quantity and priceGoldPerItem. Agency orders also
require agencyId; personal orders omit it. `201` confirms an order; `202`
returns a pending placement. GET `/api/v1/market/placements/{placementId}`
reads its outcome for the initiating player. GET and DELETE
`/api/v1/market/orders/{orderId}` validate current owner permission. DELETE
returns `202` while cancellation is pending and `200` when it is confirmed.
GET `/api/v1/market/orders` lists the open book, including allocated quantity.

The one-second worker uses database claims, stable command keys, and Assets
status/receipt reads; retries do not need player tokens. Requests time out
at five seconds. Metrics include `market.pending.operations`,
`market.pending.oldest.seconds`, `market.recovery.attempts`, and
`market.recovery.failures`. Conflicts retain reservations and allocated
quantities; investigate using operation IDs and the Assets receipts rather than
editing balances or replaying with new keys.

Reset Core, Assets and Market together with all writers stopped. The deterministic
orders here reference reservations in Assets' import.sql, whose initial
balances already exclude those assets. Bootstrap once, then run services
with schema validation. Do not run destructive schema creation on replicas.
See [architecture](../../MARKET_ARCHITECTURE.md) and
[recovery protocol](../../ASSETS_RECOVERY.md).
