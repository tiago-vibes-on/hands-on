# Assets and Market recovery protocol

Status: implemented by Market's durable placement, trade, and cancellation
records, worker, pending API responses and frontend. Assets implements private
authenticated commands, reservation/receipt reads and permanent closure.
Core also implements durable equipment, Quest-start and Expedition-credit
workflows with Hero fences and verified Assets receipts. The local extraction
passed outage, restart and exact-replay checks on 2026-10-02.
See [architecture](MARKET_ARCHITECTURE.md) for actual service ownership.

## Durable identity and states

Market commits a placement intent before calling Assets. Store its immutable
request, initiating identity, reservation key, resulting order ID, state,
attempt count, next attempt time, and worker lease. Retries preserve every key
and payload. Never persist player bearer tokens in intents, logs, or metrics.

| Record | Pending state | Confirmed transition |
| --- | --- | --- |
| Placement intent | `PENDING_RESERVATION` | Reservation confirmed: create one `OPEN` order; rejection or abandonment requires confirmed closure |
| Abandoned placement | `PENDING_ABORT` | Assets closure confirmed: `REJECTED` or `ABANDONED`; no visible order |
| Trade | `PENDING_SETTLEMENT` | Matching receipt confirmed: `SETTLED`, with fills committed once |
| Order cancellation | `PENDING_CANCEL` | Pending trades resolved and reservation closed: `CANCELLED` |

Pending placements are never open or matchable. Pending trade quantities
cannot match again. `PENDING_CANCEL` freezes matching immediately, but does
not claim resources were refunded. Commit pending operations and quantity
allocations in one Market transaction protected by order locks. Compare Assets
response keys, kind, quantities, and prices with the durable operation before
committing the Market transition. Reservation responses must also match the
requester, owner, item, resource type, and original quantity/price limits.

The placement API returns `201` with an order when both reservation
and order creation are confirmed, or `202` with a placement reference while
pending. The initiating subject reads the placement status, and the browser
keeps unresolved IDs across reloads. Confirmed order responses also include
quantityPending so allocated quantities are not advertised as available.

## Placement and lost responses

1. Persist `PENDING_RESERVATION`, then call Assets with the dedicated service
   credential and the player's Assets-audience token. Assets checks permissions
   and records the requester with the debit.
2. On confirmation, compare the entire reservation with the intent. Create
   the order and complete the intent in one Market transaction. A unique
   placement/order relation prevents two workers creating two orders.
3. On timeout, disconnect, or server error, keep the intent pending. Query
   Assets with the original reservation key. An exact committed reservation
   recovers success even after the player token expires.
4. A status `404` is inconclusive while an earlier call may still commit.
   Retry only with a valid token for the same requester. If it expired and
   no reservation is confirmed, request reauthentication or abandon safely.
5. To abandon or reject, persist `PENDING_ABORT`, then close the reservation
   using a stable operation key. Only confirmed closure or its receipt
   permits a terminal intent. Closure and reservation share an Assets lock:
   closure either refunds an existing reservation or fences a later debit.
   If confirmation is lost, recover its receipt or retry the identical command.

Do not infer absence from one `404`, allocate a replacement reservation key,
or rely on a timeout to release gold. A new intent replacing an abandoned
placement requires confirmation that the old key is closed.

## Trades and cancellation

Persist each trade and make both quantities unavailable before settlement.
Retries and receipt reads use the original operation key. Lost confirmation
never permits rematching. After receipt confirmation, update both fills and
the trade in one Market transaction. Assets separately commits resource
movements, fee, and buyer refund in one Assets transaction. HTTP or broker
delivery is not a shared transaction between services.

Cancellation records `PENDING_CANCEL` and freezes matching under the order
lock. Resolve already-allocated trades through receipt reads or identical
settlement retries. Then close the reservation with a stable operation key.
Compare the refunded quantity with the reconciled unfilled quantity before
marking `CANCELLED`. A mismatch requires investigation and must not reopen
matching. Closing is terminal; partial release is for explicitly bounded
nonterminal operations.

Settlement/refund recovery needs no live player token: it acts within earlier
authorizations. New placement and player cancellation still need current user
permissions. Leadership changes do not redirect an old reservation's assets.

## Retries, replicas, and restarts

- External calls have a five-second deadline. Retry transport failures and
  `5xx` with jittered exponential delays starting at one second and capped at
  30 seconds. Preserve keys and payloads.
- Claim pending records in a short transaction with a 60-second
  lease and a version check. Do not keep a Market transaction open during
  Assets calls. A new worker recovers expired leases after a restart.
- Persist responses/transitions with a lease/version check. Assets idempotency
  protects duplicate delivery when an old worker resumes. Market uniqueness
  and row locks prevent duplicate orders and fills.
- Player `401` requires reauthentication or abandonment; background recovery
  sends no player token. Service `403` or disabled-API `503` requires fixing
  configuration while preserving pending work.
- Changed-payload `409`, incompatible settlement, or unexpected receipts
  stop the operation for investigation. Never replace its key, rematch its
  quantity, or infer a refund.
- Attempt limits never convert pending work into success or erase it. Surface
  work older than five minutes with operation kind, age, retry count, and a
  sanitized last-error category.

## Reconciliation and retention

Continuously scan pending records and expired leases. Reconcile by exact
reservation or operation key. Operators use the same receipt/closure
protocol as workers; direct wallet edits and deleting intents are not
recovery methods. Retain completed receipts and closures indefinitely until
an explicit retention protocol prevents redelivery of old commands.

Before removing Assets's Market writer, test worker restarts at every transaction
boundary, duplicate callers across replicas, lost responses, Assets outages,
token expiry, partial fills, cancellation during settlement, and closure races.
Verify conservation of gold minus the fee and item quantity, no negative
balances, one visible order per placement, and eventual closure or settlement
of accepted work. Assets component tests cover primitive accounting,
authorization, receipts, and closure races. Market workflow tests exercise
HTTP faults, stale claims and concurrent workers; browser tests exercise
pending responses, lost confirmations, reloads and exact retries.

## Core equipment, Quest payment and Expedition recovery

Core's `asset_workflow` stores actor, immutable request/command, state, attempts,
due time and a 60-second claim token. Hero and Party `pending_asset_operation`
fences prevent conflicting mutations. The worker sends stable commands outside
Core transactions and verifies their echoed request, key, kind and loadouts.
`APPLIED` completes the Core transition; a valid `REJECTED` receipt clears its
fences. `CONFLICT` preserves the fence for investigation. Expired claims may be
replaced; stale claim tokens cannot complete or reschedule the operation.

Use the public initiating-Manager status endpoint to inspect pending work.
Operators correlate the operation key with Core's workflow and Assets' immutable
command receipt through private service APIs. Restore dependencies and let the
worker retry. Never edit balances, clear eligibility fences without proof, or
send a new key for an uncertain economic command. A protocol conflict requires
repairing the inconsistent state with the original receipt and command intact;
there is no public operator override.

Expedition return remains frozen until Assets credits carried resources and Core
applies Hero progression. Capacity overflow returns transient `503` without a
receipt, allowing the same command after capacity is freed. Unknown catalog IDs
or contradictory receipts retain the pending aggregate for investigation. The
broker acknowledges only after both confirmations; duplicate delivery reads the
Core cursor and cannot repeat credit or XP.

Coupled lab reset requires an exact passing six-image archive and no unfinished
Expeditions or Core/Market workflows. Promotion drains all application entry
points and asset writers before checking stable data. Core, Assets and Market
seeds reset together. A failed reset stops writers and requires inspection;
restoring old images cannot undo a database reset.
