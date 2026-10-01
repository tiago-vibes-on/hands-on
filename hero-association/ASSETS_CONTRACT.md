# Assets contract in Core

Status: private Core implementation, not yet the live Market write path. This
contract prepares the first Market extraction described in
[Market architecture](MARKET_ARCHITECTURE.md). There is no separate Assets
service or public Assets endpoint.

Core owns Manager and agency gold and item inventories. A reservation deducts
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

The caller must establish the initiating Manager's identity and agency
permissions before invoking this private service. The service validates
resource ownership and math, but it is **not** a substitute for authorization.
No Market-to-Core API or service-to-service identity contract exists yet.
The current public Market path still uses its original single-Core-transaction
implementation, so the new rows are not populated by ordinary order requests.
Gold transfers also remain on their existing, non-idempotent path.

Next: define authenticated private commands and transfer receipts; connect
Market to this contract only with a durable pending-operation/retry protocol;
then remove the old Market writer after end-to-end verification. Do not let
two writers reserve the same resources for one order.
