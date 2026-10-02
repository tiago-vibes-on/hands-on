package io.tiagovibeson.heroassociation.market;

import java.util.UUID;
import io.tiagovibeson.heroassociation.market.MarketContracts.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import org.hibernate.annotations.Check;

@Entity @Table(name = "market_order", indexes = @Index(name = "idx_market_order_work", columnList = "item_id,side,state,price_gold_per_item,priority"))
@Check(constraints = "quantity_remaining >= 0 and quantity_pending >= 0 and quantity_pending <= quantity_remaining")
public class MarketOrder extends RecoveryRecord {
    @Column(name = "placement_id", nullable = false, updatable = false, unique = true) public UUID placementId;
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
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) public OrderState state;
    @Column(name = "initial_quantity", nullable = false, updatable = false) public int initialQuantity;
    @Column(name = "quantity_remaining", nullable = false) public int quantityRemaining;
    @Column(name = "quantity_pending", nullable = false) public int quantityPending;
    @Column(name = "price_gold_per_item", nullable = false, updatable = false) public long priceGoldPerItem;
    @Column(nullable = false, updatable = false) public long priority;
    @Column(name = "pending_since") public java.time.Instant pendingSince;
    protected MarketOrder() { }
    MarketOrder(MarketPlacement placement, long priority) {
        id = placement.orderId; placementId = placement.id; reservationKey = placement.reservationKey;
        closureKey = placement.closureKey; ownerType = placement.ownerType; ownerId = placement.ownerId;
        ownerName = placement.ownerName; itemId = placement.itemId; itemCode = placement.itemCode;
        itemName = placement.itemName; itemSymbol = placement.itemSymbol; side = placement.side;
        state = OrderState.OPEN; initialQuantity = placement.quantity; quantityRemaining = placement.quantity;
        priceGoldPerItem = placement.priceGoldPerItem; this.priority = priority;
    }
    int available() { return quantityRemaining - quantityPending; }
    boolean sameOwner(MarketOrder other) { return ownerType == other.ownerType && ownerId.equals(other.ownerId); }
    void complete(int quantity) {
        if (quantity <= 0 || quantityPending < quantity || quantityRemaining < quantity) throw new IllegalStateException("Trade allocation is inconsistent.");
        quantityPending -= quantity; quantityRemaining -= quantity;
        if (state != OrderState.PENDING_CANCEL && state != OrderState.CONFLICT) state = quantityRemaining == 0 ? OrderState.FILLED : OrderState.PARTIALLY_FILLED;
    }
}
