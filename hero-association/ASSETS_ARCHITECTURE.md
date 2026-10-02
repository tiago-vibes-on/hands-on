# Assets service extraction

Status: extracted and cut over locally on 2026-10-02. Assets is the sole owner of wallets,
item and rune catalogs, inventories, equipped Hero rune slots, reservations,
receipts, and resource postings. Core retains identity, agency permission,
Hero eligibility/progression, Party and Quest state. Market retains its order
book and durable recovery. No service reads another service's database.

## Mutation and read audit

| Current Core entry point | Asset responsibility | New owner or caller |
| --- | --- | --- |
| Private Market reservation/release/settlement/closure | Available gold/items, held quantities, fees, refunds and immutable receipts | Assets; Market calls its private API directly |
| Gold transfers | Atomic Manager/agency wallet movement and exact request replay | Assets public API through BFF; Core resolves identity, names and permissions |
| Rune equip/replace/unequip | Inventory movement and five equipped Hero slots | Assets mutation; Core persists authorization and a Hero eligibility fence |
| Quest start | Manager-to-agency borrowing payment and pinned rune loadouts | Core durable Quest-start workflow; Assets applies the payment once |
| Expedition return | Aggregated carried gold/items/runes | Core settlement workflow calls Assets before acknowledging the aggregate |
| Account and agency projections | Wallets, loose inventories and current Hero loadouts | Core composes bounded private Assets snapshots |
| Quest combat and Expedition admission | Pinned rune IDs and effects | Core reserves Hero eligibility before obtaining an Assets snapshot |
| Feed item attachment | Catalog display and current stock validation; no debit | Core reads Assets and stores an attachment snapshot |
| Manager/agency creation | Empty wallet and inventory | Assets creates an empty wallet on the first trusted request |

Recruitment has no purchase price today. Setting a borrowing fee changes Hero
configuration, not money. Quest reward fields are display metadata; economic
Quest rewards remain deferred. Agency upgrades and battle consumables have no
implemented resource mutation and are outside this extraction.

## Cross-service contracts

Market keeps stable reservation and operation keys and its existing recovery
protocol. The Assets private Market contract keeps its wire shape while its
host changes. Context/reserve calls validate the player's token and obtain
current identity/leadership from Core before the Assets transaction. Recovery
uses the Market service credential and previously authorized reservations.

Core has a separate credential for private Assets snapshots and commands.
Assets has no Core database credentials. Its public gold-transfer endpoint
validates its own Keycloak audience; Core authorizes normalized owner names
using the original player token. Completed transfers bind the initiating
subject and immutable request, so an exact replay needs no new permission
grant or balance movement.

Equipment and Quest-start requests carry UUIDv7 operation keys. Core records
the actor, immutable command and pending state while locking the affected
Heroes. A persisted Hero fence prevents another loadout mutation, Party edit,
Quest start or Expedition admission until the operation completes. Core then
calls Assets outside its transaction. Assets commits the whole command and
its immutable receipt atomically. Core validates that receipt before finishing
its own state change and clearing the fence. Unknown delivery stays pending;
database claims and a worker retry the same command after a restart. A
definitive rejection clears the fence without starting a Quest. Receipt
conflicts retain the fence for investigation.

Expedition admission reserves Heroes before reading equipment. Return keeps
the existing immutable aggregate until both Assets credit and Core Hero
progression are applied. Broker acknowledgment follows both confirmations;
neither a timeout nor a duplicate message can repeat asset credit or Hero XP.
No player token is retained for background recovery.

Assets locks owners in a consistent order and records available-resource
postings alongside reservation and command receipts. Wallet/inventory changes
and their receipts share one local transaction. These contracts use eventual
completion across databases rather than a distributed database transaction.

## Verification and cutover

Component tests must cover concurrent spending, exact retries, changed payloads,
authorization, equipment conservation, partial Market fills, fee/refund math,
and resource overflow. Core workflow tests must lose responses after Assets
commit, restart recovery, and race equipment against Quest/Expedition entry.
The full isolated gate also stops Assets, verifies a pending Hero fence, restarts
Core and confirms exact-once completion after Assets recovery. It exercises
existing player flows with separate
Core, Assets and Market databases and the exact six archived images.

Before Flyway, a schema-changing cutover stops all three writers and recreates
their matching deterministic seeds from the verified archive. Refuse active
Expeditions or unresolved Core/Market workflows before resetting shared lab
data. Keep the user's frontend hot-reload route after promotion. Remove Core's
asset tables, economic writes, and old private Market endpoint at cutover.

## Verified local result

The six-image `assets-extraction-20261002-v1` JVM archive passed the isolated
k3d gate, including 16 browser/API cases, three Assets outage/restart phases,
lost equipment and Quest responses, cache fallback, BFF session recovery and k6.
Component suites, six frontend test files, lint/build and six delivery test files
passed. Core's suite includes 101 main cases plus broker and cache-outage checks;
Assets has 36 cases covering balances, equipment, receipts and private permissions.

Daily promotion recreated the validated Core, Assets and Market schemas from
matching archived seeds. Nine daily browser cases, Expedition settlement, the
full Map journey and k6 passed. All ten application Pod image IDs matched the
archive. The Core schema audit found zero retired asset tables and zero wallet
columns; Assets holds 16 wallets, two item definitions, seven rune definitions
and 15 seeded equipped slots. Frontend hot reload is restored after promotion;
the sign-in/sign-out browser smoke test passed, and the k3d gateway serves Vite
HTML and its development client with HTTP 200.
The ignored archive contains the passing E2E and promotion records. Native
Compose/build wiring is updated; native compilation was outside this JVM gate.
