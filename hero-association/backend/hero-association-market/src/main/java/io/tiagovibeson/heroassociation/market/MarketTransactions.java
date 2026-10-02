package io.tiagovibeson.heroassociation.market;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import io.tiagovibeson.heroassociation.market.MarketContracts.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

/** Local transactions never include a network call to Core. */
@ApplicationScoped
@Transactional
public class MarketTransactions {
    @Inject EntityManager em;

    public MarketPlacement placement(UUID id) { return em.find(MarketPlacement.class, id); }
    public OrderView order(UUID id) { return OrderView.from(requireOrder(id)); }
    public List<OrderView> book() {
        return em.createQuery("from MarketOrder where state in (:open, :partial) order by createdAt, id", MarketOrder.class)
                .setParameter("open", OrderState.OPEN).setParameter("partial", OrderState.PARTIALLY_FILLED)
                .getResultList().stream().map(OrderView::from).toList();
    }
    public MarketPlacement stage(String subject, PlaceRequest request, OwnerContext context) {
        keyLock(request.placementId());
        MarketPlacement existing = em.find(MarketPlacement.class, request.placementId());
        if (existing != null) {
            if (!existing.matches(subject, request)) throw new MarketException(409, "Placement ID was already used for a different request.");
            return existing;
        }
        MarketPlacement placement = new MarketPlacement(subject, request, context);
        em.persist(placement); em.flush(); return placement;
    }
    public PlacementClaim claimPlacement(UUID id, Instant now) {
        MarketPlacement placement = locked(MarketPlacement.class, id);
        if (placement == null || (placement.state != PlacementState.PENDING_RESERVATION && placement.state != PlacementState.PENDING_ABORT)
                || !claim(placement, now)) return null;
        return placement.claim();
    }
    public PlacementClaim beginAbort(PlacementClaim claim, int rejectionStatus, String message) {
        MarketPlacement placement = locked(MarketPlacement.class, claim.id());
        if (!leased(placement, claim.leaseId()) || placement.state != PlacementState.PENDING_RESERVATION) return null;
        placement.state = PlacementState.PENDING_ABORT;
        placement.rejectionStatus = rejectionStatus;
        placement.message = message;
        return placement.claim();
    }
    public void finishAbort(PlacementClaim claim) {
        MarketPlacement placement = locked(MarketPlacement.class, claim.id());
        if (!leased(placement, claim.leaseId()) || placement.state != PlacementState.PENDING_ABORT) return;
        placement.state = placement.rejectionStatus == 0 ? PlacementState.ABANDONED : PlacementState.REJECTED;
        clearLease(placement);
    }
    public void publish(PlacementClaim claim) {
        MarketBook book = lockBook(claim.itemId());
        MarketPlacement placement = locked(MarketPlacement.class, claim.id());
        if (!leased(placement, claim.leaseId()) || placement.state != PlacementState.PENDING_RESERVATION) return;
        MarketOrder order = new MarketOrder(placement, ++book.nextSequence);
        em.persist(order);
        placement.state = PlacementState.OPEN; clearLease(placement);
        em.flush(); match(order);
    }
    private void match(MarketOrder incoming) {
        String direction = incoming.side == Side.BUY ? "asc" : "desc";
        String condition = incoming.side == Side.BUY ? "<= :price" : ">= :price";
        List<MarketOrder> candidates = em.createQuery("from MarketOrder where itemId = :item and side = :side "
                + "and state in (:open, :partial) and quantityRemaining > quantityPending and priceGoldPerItem "
                + condition + " order by priceGoldPerItem " + direction + ", priority, id", MarketOrder.class)
                .setParameter("item", incoming.itemId).setParameter("side", incoming.side == Side.BUY ? Side.SELL : Side.BUY)
                .setParameter("open", OrderState.OPEN).setParameter("partial", OrderState.PARTIALLY_FILLED)
                .setParameter("price", incoming.priceGoldPerItem).getResultList();
        for (MarketOrder resting : candidates) {
            if (incoming.available() == 0) break;
            if (incoming.sameOwner(resting) || resting.available() == 0) continue;
            int quantity = Math.min(incoming.available(), resting.available());
            MarketOrder buyer = incoming.side == Side.BUY ? incoming : resting;
            MarketOrder seller = incoming.side == Side.SELL ? incoming : resting;
            buyer.quantityPending += quantity; seller.quantityPending += quantity;
            em.persist(new MarketTrade(buyer, seller, quantity, resting.priceGoldPerItem));
        }
    }
    public TradeClaim claimTrade(UUID id, Instant now) {
        MarketTrade trade = locked(MarketTrade.class, id);
        if (trade == null || trade.state != TradeState.PENDING_SETTLEMENT || !claim(trade, now)) return null;
        return trade.claim();
    }
    public void finishTrade(TradeClaim claim) {
        lockBook(claim.itemId());
        MarketTrade trade = locked(MarketTrade.class, claim.id());
        if (!leased(trade, claim.leaseId()) || trade.state != TradeState.PENDING_SETTLEMENT) return;
        MarketOrder buyer = locked(MarketOrder.class, claim.buyerOrderId());
        MarketOrder seller = locked(MarketOrder.class, claim.sellerOrderId());
        buyer.complete(claim.quantity()); seller.complete(claim.quantity());
        trade.state = TradeState.SETTLED; clearLease(trade);
    }
    public OrderView freezeCancellation(UUID id) {
        MarketOrder snapshot = requireOrder(id);
        lockBook(snapshot.itemId);
        MarketOrder order = locked(MarketOrder.class, id);
        if (order.state == OrderState.FILLED) throw new MarketException(409, "A filled order cannot be cancelled.");
        if (order.state == OrderState.CONFLICT) throw new MarketException(409, "This order needs recovery investigation.");
        if (order.state != OrderState.CANCELLED && order.state != OrderState.PENDING_CANCEL) {
            order.state = OrderState.PENDING_CANCEL; order.pendingSince = Instant.now(); order.nextAttemptAt = Instant.EPOCH;
        }
        return OrderView.from(order);
    }
    public CancellationClaim claimCancellation(UUID id, Instant now) {
        MarketOrder order = locked(MarketOrder.class, id);
        if (order == null || order.state != OrderState.PENDING_CANCEL || order.quantityPending != 0 || !claim(order, now)) return null;
        return new CancellationClaim(id, order.leaseId, order.itemId, order.reservationKey, order.closureKey, order.quantityRemaining);
    }
    public void finishCancellation(CancellationClaim claim) {
        lockBook(claim.itemId());
        MarketOrder order = locked(MarketOrder.class, claim.id());
        if (!leased(order, claim.leaseId()) || order.state != OrderState.PENDING_CANCEL) return;
        if (order.quantityPending != 0 || order.quantityRemaining != claim.quantityRemaining()) throw new IllegalStateException("Cancellation quantity changed after closure.");
        order.state = OrderState.CANCELLED; clearLease(order);
    }
    public void placementFailure(PlacementClaim claim, String message, boolean conflict, Instant now) {
        MarketPlacement placement = locked(MarketPlacement.class, claim.id());
        if (!leased(placement, claim.leaseId())) return;
        if (conflict) { placement.state = PlacementState.CONFLICT; placement.message = "Placement needs recovery investigation."; }
        retry(placement, message, now);
    }
    public void tradeFailure(TradeClaim claim, String message, boolean conflict, Instant now) {
        lockBook(claim.itemId());
        MarketTrade trade = locked(MarketTrade.class, claim.id());
        if (!leased(trade, claim.leaseId())) return;
        if (conflict) {
            trade.state = TradeState.CONFLICT;
            locked(MarketOrder.class, claim.buyerOrderId()).state = OrderState.CONFLICT;
            locked(MarketOrder.class, claim.sellerOrderId()).state = OrderState.CONFLICT;
        }
        retry(trade, message, now);
    }
    public void cancellationFailure(CancellationClaim claim, String message, boolean conflict, Instant now) {
        lockBook(claim.itemId());
        MarketOrder order = locked(MarketOrder.class, claim.id());
        if (!leased(order, claim.leaseId())) return;
        if (conflict) order.state = OrderState.CONFLICT;
        retry(order, message, now);
    }
    public List<UUID> pendingPlacements(Instant now) {
        return pending("MarketPlacement", "state in ('PENDING_RESERVATION', 'PENDING_ABORT')", now);
    }
    public List<UUID> pendingTrades(Instant now) { return pending("MarketTrade", "state = 'PENDING_SETTLEMENT'", now); }
    public List<UUID> pendingCancellations(Instant now) { return pending("MarketOrder", "state = 'PENDING_CANCEL'", now); }
    private List<UUID> pending(String entity, String condition, Instant now) {
        return em.createQuery("select id from " + entity + " where " + condition
                + " and nextAttemptAt <= :now and (leaseUntil is null or leaseUntil <= :now) order by createdAt, id", UUID.class)
                .setParameter("now", now).setMaxResults(20).getResultList();
    }
    public long pendingCount(String kind) {
        return em.createQuery("select count(*) from " + pendingEntity(kind) + " where " + pendingCondition(kind), Long.class).getSingleResult();
    }
    public long oldestPendingSeconds(String kind, Instant now) {
        String timestamp = "cancellation".equals(kind) ? "coalesce(pendingSince, createdAt)" : "createdAt";
        Instant oldest = em.createQuery("select min(" + timestamp + ") from " + pendingEntity(kind) + " where " + pendingCondition(kind), Instant.class).getSingleResult();
        return oldest == null ? 0 : Math.max(0, java.time.Duration.between(oldest, now).toSeconds());
    }
    private String pendingEntity(String kind) {
        return switch (kind) { case "placement" -> "MarketPlacement"; case "trade" -> "MarketTrade"; default -> "MarketOrder"; };
    }
    private String pendingCondition(String kind) {
        return switch (kind) { case "placement" -> "state in ('PENDING_RESERVATION','PENDING_ABORT','CONFLICT')";
            case "trade" -> "state in ('PENDING_SETTLEMENT','CONFLICT')"; default -> "state in ('PENDING_CANCEL','CONFLICT')"; };
    }
    private MarketOrder requireOrder(UUID id) {
        MarketOrder order = em.find(MarketOrder.class, id);
        if (order == null) throw new MarketException(404, "The requested order was not found.");
        return order;
    }
    private MarketBook lockBook(UUID itemId) {
        em.createNativeQuery("insert into market_book (id, next_sequence) values (:id, 0) on conflict (id) do nothing")
                .setParameter("id", itemId).executeUpdate();
        return locked(MarketBook.class, itemId);
    }
    private <T> T locked(Class<T> type, UUID id) {
        T record = em.find(type, id, LockModeType.PESSIMISTIC_WRITE);
        if (record != null) em.refresh(record);
        return record;
    }
    private void keyLock(UUID id) {
        em.createNativeQuery("select 1 from pg_advisory_xact_lock(:key)", Integer.class)
                .setParameter("key", id.getMostSignificantBits() ^ id.getLeastSignificantBits()).getSingleResult();
    }
    private boolean claim(RecoveryRecord record, Instant now) {
        if (record.nextAttemptAt.isAfter(now) || (record.leaseUntil != null && record.leaseUntil.isAfter(now))) return false;
        record.leaseId = UuidV7.next(); record.leaseUntil = now.plusSeconds(60); record.attempts++;
        return true;
    }
    private boolean leased(RecoveryRecord record, UUID leaseId) { return record != null && leaseId.equals(record.leaseId); }
    private void clearLease(RecoveryRecord record) { record.leaseId = null; record.leaseUntil = null; record.lastError = null; }
    private void retry(RecoveryRecord record, String message, Instant now) {
        long delay = Math.min(30000, (1000L << Math.min(record.attempts - 1, 5)));
        delay = Math.min(30000, delay + ThreadLocalRandom.current().nextLong(Math.max(1, delay / 5)));
        clearLease(record); record.nextAttemptAt = now.plusMillis(delay);
        record.lastError = message.substring(0, Math.min(255, message.length()));
    }
}
