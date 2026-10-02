package io.tiagovibeson.heroassociation.market;

import java.util.Objects;
import java.util.UUID;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import io.tiagovibeson.heroassociation.market.MarketContracts.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

@Entity @Table(name = "market_placement", indexes = @Index(name = "idx_market_placement_work", columnList = "state,next_attempt_at,lease_until"))
public class MarketPlacement extends RecoveryRecord {
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) public PlacementState state;
    @Column(name = "requester_subject", nullable = false, updatable = false) public String requesterSubject;
    @Column(name = "requester_manager_id", nullable = false, updatable = false) public UUID requesterManagerId;
    @Column(name = "order_id", nullable = false, updatable = false, unique = true) public UUID orderId;
    @Column(name = "reservation_key", nullable = false, updatable = false, unique = true) public UUID reservationKey;
    @Column(name = "closure_key", nullable = false, updatable = false, unique = true) public UUID closureKey;
    @Enumerated(EnumType.STRING) @Column(name = "owner_type", nullable = false, updatable = false) public OwnerType ownerType;
    @Column(name = "owner_id", nullable = false, updatable = false) public UUID ownerId;
    @Column(name = "owner_name", nullable = false, updatable = false) public String ownerName;
    @Column(name = "item_id", nullable = false, updatable = false) public UUID itemId;
    @Column(name = "item_code", nullable = false, updatable = false) public String itemCode;
    @Column(name = "item_name", nullable = false, updatable = false) public String itemName;
    @Column(name = "item_symbol", nullable = false, updatable = false) public String itemSymbol;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) public Side side;
    @Column(nullable = false, updatable = false) public int quantity;
    @Column(name = "price_gold_per_item", nullable = false, updatable = false) public long priceGoldPerItem;
    @Column(name = "rejection_status", nullable = false) public int rejectionStatus;
    public String message;

    protected MarketPlacement() { }
    public MarketPlacement(String subject, PlaceRequest request, OwnerContext context) {
        id = request.placementId(); state = PlacementState.PENDING_RESERVATION;
        requesterSubject = subject; requesterManagerId = context.managerId();
        orderId = UuidV7.next(); reservationKey = UuidV7.next(); closureKey = UuidV7.next();
        ownerType = context.ownerType(); ownerId = context.ownerId(); ownerName = context.ownerName();
        itemId = context.item().id(); itemCode = context.item().code();
        itemName = context.item().name(); itemSymbol = context.item().symbol();
        side = request.side(); quantity = request.quantity(); priceGoldPerItem = request.priceGoldPerItem();
    }
    boolean matches(String subject, PlaceRequest request) {
        return requesterSubject.equals(subject) && ownerType == request.ownerType()
                && (ownerType != OwnerType.AGENCY || Objects.equals(ownerId, request.agencyId()))
                && itemId.equals(request.itemId()) && side == request.side()
                && quantity == request.quantity() && priceGoldPerItem == request.priceGoldPerItem();
    }
    PlacementClaim claim() {
        return new PlacementClaim(id, leaseId, state, requesterSubject, requesterManagerId, orderId,
                reservationKey, closureKey, ownerType, ownerId, ownerName, itemId, itemCode, itemName,
                itemSymbol, side, quantity, priceGoldPerItem, rejectionStatus, message);
    }
    PlacementView view() { return new PlacementView(id, state.name(), state == PlacementState.OPEN ? orderId : null, message); }
}
