# Assets service contract

Assets is an independent service and the resource authority for Market, transfers,
equipment, Quest fees and Expedition returns. Core owns identity, agency
permission, Hero eligibility/progression and durable orchestration. No service
reads another service's database. See [the extraction audit](ASSETS_ARCHITECTURE.md).

Rune ownership has three scopes: Manager inventory and agency inventory (`asset_stack`), and equipped Hero slots (`hero_rune`). Heroes
have no unequipped rune inventory or gold. An agency member can equip an
available rune from their own or that agency's inventory onto an agency Hero
or one of their personal Heroes at the agency. Equipping transfers it to the
Hero slot; replacing or unequipping sends the previous rune to the acting
Manager's inventory by default. Each command persists atomically. Heroes on
quests or Expeditions cannot change equipment. Agency rune access does not
change the existing leader-only agency gold and Market permissions.

Assets owns Manager and agency gold and item inventories. A reservation deducts
the selected owner's available gold or item quantity in the same database
transaction that records an `asset_reservation` row. BUY reservations bind an
item, quantity, and maximum gold price per item; SELL reservations bind an
item, quantity, and minimum gold price per item. Settlement must stay within
both limits. Each reservation has a caller-generated UUIDv7 key. An
exact retry returns the same row without a second debit. Reusing a key with
different inputs is rejected.

An `asset_operation_receipt` row with a distinct UUIDv7 operation key records
each release or trade settlement. An exact retry returns that receipt without
moving resources again. A changed payload with the same key is rejected.
Releases return only the specified remaining quantity. A trade consumes equal
quantities from a buyer's gold reservation and a seller's item reservation,
credits the buyer's items, refunds any difference between the buyer's limit
and execution prices, and credits the seller after the existing 10% market
fee. Partial fills leave the rest reserved. Owners are locked in a stable
order and reservations are locked in key order to prevent overspending and
reduce deadlock risk.

## Private authentication and authorization

Market-facing `/internal/v1/assets/**` requests, excluding `/core/**`, require the dedicated
`X-Hero-Association-Market-Service-Key` credential, configured only in Assets
and its trusted caller through `HERO_ASSOCIATION_ASSETS_MARKET_SERVICE_KEY`.
The credential must contain at least 32 characters. Missing or too-short
configuration fails closed with `503`; missing or incorrect request
credentials return `403`. Expedition's credential cannot authorize Assets.
The BFF does not route this prefix or forward the credential. Use private,
TLS-protected service connections and secret configuration; never send the
credential to a browser.

Creating a reservation also requires the initiating player's bearer token.
Assets validates its Keycloak issuer, signature, expiry, subject and Assets
audience, then calls Core with the original token and the separate Core service
credential. Core resolves the Manager from that token,
never from a caller-supplied identity header. `MANAGER` ownership must match
that Manager; `AGENCY` requires leadership of the specified agency.
Core authorizes the request before the local Assets mutation; no remote
permission call holds an Assets wallet lock. The reservation records
`requesterManagerId`; a changed requester cannot reuse its key.

Settlement, release, closure, and status reads use the service credential
without a player bearer token. They validate previously authorized
reservations, their owners, item, price, and remaining quantities. Existing
orders can settle or refund after sign-out, token expiry, or a leadership
change. Market separately authorizes new player
cancellation requests.

## HTTP contract

All bodies are JSON. Persistent IDs and command keys use RFC 9562 UUIDv7,
including the RFC UUID variant. Structural validation and invalid command
keys return `400`; player permission failures return `403`; resource or
payload conflicts return `409`. Successful commands return `200`.

| Method and path under `/internal/v1/assets` | Request | Result |
| --- | --- | --- |
| `POST /context` | `ownerType`, optional `ownerId`, optional `itemId`; player bearer token required | Current Manager identity, authorized owner ID/name and optional catalog definition; does not debit assets |
| `POST /reservations` | `reservationKey`, `ownerType`, `ownerId`, `resourceType`, `itemId`, `quantity`, `unitPriceGoldPerItem`; player bearer token required | Reservation with requester, initial and remaining quantities, and `closed: false` |
| `GET /reservations/{reservationKey}` | Service credential | Current reservation, closure-only result with `closed: true`, or `404` when neither is committed |
| `POST /releases` | `operationKey`, `reservationKey`, `quantity` | Immutable `RELEASE` receipt |
| `POST /settlements` | `operationKey`, `buyerReservationKey`, `sellerReservationKey`, `quantity`, `executionPriceGoldPerItem` | Immutable `TRADE_SETTLEMENT` receipt |
| `POST /reservations/{reservationKey}/close` | `operationKey` | Immutable `CLOSE` receipt; refunds all remaining resources and permanently fences a late reservation |
| `GET /operations/{operationKey}` | Service credential | Release, settlement, or closure receipt, or `404` if none is committed |

`ownerType` is `MANAGER` or `AGENCY`; `resourceType` is `GOLD` for BUY or
`ITEM` for SELL. Quantities and whole-gold unit prices must be positive.
Receipts include operation key, kind, first and second reservation keys,
quantity, and execution price. Closure-only reservation results have null
owner and item fields and zero quantities. A status `404` does not prove
that another request cannot still commit: use closure to abandon a placement.

Command keys are serialized with PostgreSQL transaction advisory locks,
including keys with no row. `asset_reservation_closure` fences abandoned
keys whether closure reaches Core before or after reservation. Closing
refunds only the still-unfilled quantity. Market must resolve pending trades
before closing an order. Receipts and closures have no expiry or pruning.

## Gold transfers

`POST /api/v1/gold-transfers` requires a caller-generated `operationKey` in
addition to the existing direction, names, and amount. Core records a
`asset_command_receipt` in the same transaction as both wallet changes. It
binds the authenticated Manager, normalized names, direction, and amount.
Exact retries return the original response, including the operation key and
original balance snapshot. Changed inputs or another requester return `409`;
missing or non-UUIDv7 keys return `400`. Names are trimmed and matched
case-insensitively. Refresh account and agency state for current balances.

Any Manager may deposit personal gold into any agency; only its leader may
withdraw to any Manager. No market fee or agency earnings share applies.
New commands check current permissions. Replaying the same Manager's
completed receipt cannot move gold again.

The frontend saves uncertain transfer keys in session storage, scoped to
the Manager and normalized inputs. Retrying the same transfer, including
after a reload in the same tab, reuses its key. Confirmation and successful
state refresh clear it; definitive `400`/`403`/`404`/`409` rejection clears it.
Network failures, authentication expiry, and server errors retain it.

## Recovery and cutover

The public Market path calls this contract from its separate service. Core's
old economic and order writers has been removed. The [Assets recovery protocol](ASSETS_RECOVERY.md)
is implemented by Market's placements, trades, cancellations, leases and worker.
Assets records idempotent receipts and permanent reservation closures.
One order must never be reserved by both paths.

Schema changes use the pre-Flyway reset-and-seed workflow. Recreate local or
pre-production Core, Assets and Market data together before running against older
schemas, with all writers stopped. Isolated
component checks do not reset daily data.

## Core command API

`/internal/v1/assets/core/**` requires
`X-Hero-Association-Assets-Core-Service-Key`, configured through the distinct
`HERO_ASSOCIATION_ASSETS_CORE_SERVICE_KEY` in Core and Assets. Market's credential
cannot authorize this prefix. Core commands represent an already staged,
authorized and eligibility-fenced workflow; background recovery needs no player
JWT. BFF has no private credential or route.

- `POST /snapshots` takes bounded `owners: [{ ownerType, ownerId }]` (up to 128)
  and `heroes: [UUIDv7]` (up to 256); returns wallets, catalog-enriched loose stacks
  and five-slot loadout entries. Unknown owners have empty holdings.
- `POST /commands` takes `operationKey`, `kind`, `managerId` and the immutable
  operation fields. Supported kinds are `RUNE_EQUIP`, `RUNE_UNEQUIP`,
  `HERO_LOADOUT_SNAPSHOT` and `EXPEDITION_CREDIT`. Each command commits all resource
  changes, available-resource/slot postings and an immutable receipt in one local
  transaction. A changed actor, kind or payload under the same key returns `409`.
- `GET /commands/{operationKey}` returns the immutable receipt. It echoes the
  complete `request` and has `status: APPLIED | REJECTED`, optional
  `rejectionStatus`/`message`, and affected `heroes` loadouts.

Expected command rejection persists a no-mutation receipt. Transient Expedition
capacity overflow returns `503` without a receipt so the frozen return can retry.
Snapshots for admission and Quest start remain pinned on exact replay despite
later equipment changes. Core validates receipts before acknowledging its own
transition. Available postings, reservation history and immutable receipts form
the resource audit trail; there is no distributed SQL transaction.

Quest pays through `POST /internal/v1/assets/quest/rewards`, protected by the
separate `X-Hero-Association-Assets-Quest-Service-Key`. The canonical command
kind is `QUEST_REWARD`, keyed by assignment UUIDv7, bound to Manager and pinned
reward amounts. Its gold/items/runes and receipt commit atomically. Core's
credential is denied this kind; Quest's route rejects equipment, agency and
Hero fields. `QUEST_START` is retired and rejected. Capacity overflow retries
with the same key after space is freed. Unknown catalogs produce a definitive
receipt that Quest quarantines as a conflicting reward.
