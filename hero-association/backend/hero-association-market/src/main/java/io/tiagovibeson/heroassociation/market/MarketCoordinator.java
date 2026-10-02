package io.tiagovibeson.heroassociation.market;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;
import io.micrometer.core.instrument.MeterRegistry;
import io.tiagovibeson.heroassociation.market.AssetsClient.AssetsFailure;
import io.tiagovibeson.heroassociation.market.MarketContracts.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** External delivery is separated from durable state transitions and leases. */
@ApplicationScoped
public class MarketCoordinator {
    private static final Logger LOG = Logger.getLogger(MarketCoordinator.class.getName());
    @Inject MarketTransactions transactions;
    @Inject AssetsClient core;
    @Inject MeterRegistry metrics;

    public void placement(UUID id, String playerToken) {
        PlacementClaim claim = transactions.claimPlacement(id, Instant.now());
        if (claim == null) return;
        metrics.counter("market.recovery.attempts", "kind", "placement").increment();
        try {
            if (claim.state() == PlacementState.PENDING_ABORT) { closePlacement(claim); return; }
            Reservation reservation;
            if (playerToken != null) {
                try { reservation = core.reserve(claim, playerToken); }
                catch (AssetsFailure failure) {
                    if (failure.status() >= 500) throw failure;
                    var existing = core.reservation(claim.reservationKey());
                    if (existing.isPresent() && !existing.get().closed()) reservation = existing.get();
                    else {
                        PlacementClaim abort = transactions.beginAbort(claim, failure.status(), failure.getMessage());
                        if (abort != null) closePlacement(abort);
                        return;
                    }
                }
            } else {
                var existing = core.reservation(claim.reservationKey());
                if (existing.isEmpty() || existing.get().closed()) {
                    PlacementClaim abort = transactions.beginAbort(claim, 0, "Placement was abandoned before resources were confirmed. Please submit a new order.");
                    if (abort != null) closePlacement(abort);
                    return;
                }
                reservation = existing.get();
            }
            validateReservation(claim, reservation);
            transactions.publish(claim);
        } catch (RuntimeException failure) {
            transactions.placementFailure(claim, category(failure), failure instanceof ProtocolViolation, Instant.now());
            recordFailure("placement", id, failure);
        }
    }
    private void closePlacement(PlacementClaim claim) {
        Receipt receipt = core.receipt(claim.closureKey()).orElseGet(() -> core.close(claim.reservationKey(), claim.closureKey()));
        validateReceipt(receipt, claim.closureKey(), "CLOSE", claim.reservationKey(), null, receipt.quantity(), 0);
        if (receipt.quantity() != 0 && receipt.quantity() != claim.quantity()) throw new ProtocolViolation("Placement closure quantity is inconsistent.");
        transactions.finishAbort(claim);
    }
    public void trade(UUID id) {
        TradeClaim claim = transactions.claimTrade(id, Instant.now());
        if (claim == null) return;
        metrics.counter("market.recovery.attempts", "kind", "trade").increment();
        try {
            Receipt receipt = core.receipt(claim.id()).orElseGet(() -> core.settle(claim));
            validateReceipt(receipt, claim.id(), "TRADE_SETTLEMENT", claim.buyerReservationKey(),
                    claim.sellerReservationKey(), claim.quantity(), claim.priceGoldPerItem());
            transactions.finishTrade(claim);
        } catch (RuntimeException failure) {
            boolean conflict = failure instanceof ProtocolViolation || failure instanceof AssetsFailure c && c.status() >= 400 && c.status() < 500 && c.status() != 401 && c.status() != 403;
            transactions.tradeFailure(claim, category(failure), conflict, Instant.now());
            recordFailure("trade", id, failure);
        }
    }
    public void cancellation(UUID id) {
        CancellationClaim claim = transactions.claimCancellation(id, Instant.now());
        if (claim == null) return;
        metrics.counter("market.recovery.attempts", "kind", "cancellation").increment();
        try {
            Receipt receipt = core.receipt(claim.closureKey()).orElseGet(() -> core.close(claim.reservationKey(), claim.closureKey()));
            validateReceipt(receipt, claim.closureKey(), "CLOSE", claim.reservationKey(), null, claim.quantityRemaining(), 0);
            transactions.finishCancellation(claim);
        } catch (RuntimeException failure) {
            boolean conflict = failure instanceof ProtocolViolation || failure instanceof AssetsFailure c && c.status() == 409;
            transactions.cancellationFailure(claim, category(failure), conflict, Instant.now());
            recordFailure("cancellation", id, failure);
        }
    }
    public void recover() {
        Instant deadline = Instant.now().plusSeconds(20);
        var placements = transactions.pendingPlacements(Instant.now());
        var trades = transactions.pendingTrades(Instant.now());
        var cancellations = transactions.pendingCancellations(Instant.now());
        // Rotate operation kinds so a failing placement cannot starve existing trades or refunds.
        for (int index = 0; index < 20; index++) {
            if (index < trades.size()) trade(trades.get(index));
            if (Instant.now().isAfter(deadline)) return;
            if (index < cancellations.size()) cancellation(cancellations.get(index));
            if (Instant.now().isAfter(deadline)) return;
            if (index < placements.size()) placement(placements.get(index), null);
            if (Instant.now().isAfter(deadline)) return;
        }
    }
    private void validateReservation(PlacementClaim claim, Reservation reservation) {
        if (!claim.reservationKey().equals(reservation.reservationKey()) || reservation.closed()
                || !claim.requesterManagerId().equals(reservation.requesterManagerId()) || claim.ownerType() != reservation.ownerType()
                || !claim.ownerId().equals(reservation.ownerId()) || !claim.itemId().equals(reservation.itemId())
                || !Objects.equals(claim.side() == Side.BUY ? "GOLD" : "ITEM", reservation.resourceType())
                || claim.quantity() != reservation.initialQuantity() || claim.quantity() != reservation.remainingQuantity()
                || claim.priceGoldPerItem() != reservation.unitPriceGoldPerItem()) throw new ProtocolViolation("Reservation response disagrees with placement.");
    }
    private void validateReceipt(Receipt receipt, UUID operation, String kind, UUID first, UUID second, int quantity, long price) {
        if (!operation.equals(receipt.operationKey()) || !kind.equals(receipt.kind()) || !first.equals(receipt.firstReservationKey())
                || !Objects.equals(second, receipt.secondReservationKey()) || quantity != receipt.quantity()
                || price != receipt.executionPriceGoldPerItem()) throw new ProtocolViolation("Operation receipt disagrees with pending operation.");
    }
    private String category(RuntimeException failure) {
        return failure instanceof AssetsFailure core ? "assets_http_" + core.status() : failure instanceof ProtocolViolation ? "protocol_conflict" : "local_operation_failure";
    }
    private void recordFailure(String kind, UUID id, RuntimeException failure) {
        metrics.counter("market.recovery.failures", "kind", kind, "category", category(failure)).increment();
        LOG.warning("Market recovery pending: kind=" + kind + " operation=" + id + " category=" + category(failure));
    }
    public static class ProtocolViolation extends RuntimeException { public ProtocolViolation(String message) { super(message); } }
}
