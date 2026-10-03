# Hero Association Assets

An independently runnable Quarkus service and sole authority for wallets,
item/rune catalogs, loose inventory, equipped Hero slots, Market reservations,
immutable receipts and available-resource postings. It has its own PostgreSQL
`hero_association_assets` and no Core entities, repositories or database access.
Core retains player identity, agency permission and Hero/Party eligibility.

From `hero-association/backend`:

```bash
./mvnw -pl hero-association-assets -am package
./mvnw -pl hero-association-assets -am test
```

Production listens on `8085`; development uses `17085`. Configure the datasource
URL, username and separate password, Core base URL, Keycloak issuer/auth-server,
and three independent credentials of at least 32 characters:

- `HERO_ASSOCIATION_ASSETS_MARKET_SERVICE_KEY` matches Market; it authorizes
  reservation, settlement, closure and receipt recovery.
- `HERO_ASSOCIATION_ASSETS_CORE_SERVICE_KEY` matches Core; it authorizes staged
  commands and snapshots, and Core player-permission RPCs.

Public `POST /api/v1/gold-transfers` validates the Assets bearer audience and
resolves current names and permission through Core before its local mutation.
Exact transfer replay binds the subject and immutable intent; it works during a
Core outage without moving gold again. BFF routes only this public API and holds
neither private credential. New reservations additionally validate a player JWT;
settlement/refund recovery uses previously accepted authorization.

`/internal/v1/assets/core/commands` supports equipment, pinned loadout
snapshots and Expedition credit. Core persists authorization and eligibility
fences before sending these commands. Assets commits each command, its resource
changes, postings and immutable receipt atomically. Unknown delivery retries the
same UUIDv7 key. Expedition capacity overflow stays transient until space is freed.

Compose includes Assets and its own PostgreSQL. Hybrid mode uses
`deploy/k3d/hybrid.sh run assets`, validates existing schemas and forwards private
Core calls to k3d. For direct dev mode, install the pure UUID library first with
`./mvnw -pl hero-association-lib/combat-engine -am install`, then run
`../mvnw quarkus:dev` here with the environment configured.

Production replicas validate schema; they never create it. Bootstrap the coupled
Core, Assets and Market deterministic seeds once from the same verified archive.
Before Flyway, reset all three together with writers stopped and no unfinished
Expeditions or asset workflows. The full gate requires all six exact images;
component tests use disposable PostgreSQL and a Core authority contract fixture.
See [contracts](../../ASSETS_CONTRACT.md), [recovery](../../ASSETS_RECOVERY.md),
and [architecture and audit](../../ASSETS_ARCHITECTURE.md).

`HERO_ASSOCIATION_ASSETS_QUEST_SERVICE_KEY` is a third separate credential for
`POST /internal/v1/assets/quest/rewards`. It permits only `QUEST_REWARD`, bound
to the assignment ID, Manager and immutable gold/item/rune amounts. Core's key
cannot credit this reward; `QUEST_START` is retired. The resource changes,
ledger postings and exact receipt commit atomically. See
[World/Quest return rules](../../WORLD_QUEST_ARCHITECTURE.md).
