package io.tiagovibeson.heroassociation.market;

import java.util.UUID;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import io.tiagovibeson.heroassociation.market.MarketContracts.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

@Entity @Table(name = "market_trade", indexes = @Index(name = "idx_market_trade_work", columnList = "state,next_attempt_at,lease_until"))
public class MarketTrade extends RecoveryRecord {
    @Column(name = "item_id", nullable = false, updatable = false) public UUID itemId;
    @Column(name = "buyer_order_id", nullable = false, updatable = false) public UUID buyerOrderId;
    @Column(name = "seller_order_id", nullable = false, updatable = false) public UUID sellerOrderId;
    @Column(name = "buyer_reservation_key", nullable = false, updatable = false) public UUID buyerReservationKey;
    @Column(name = "seller_reservation_key", nullable = false, updatable = false) public UUID sellerReservationKey;
    @Column(nullable = false, updatable = false) public int quantity;
    @Column(name = "price_gold_per_item", nullable = false, updatable = false) public long priceGoldPerItem;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) public TradeState state;
    protected MarketTrade() { }
    MarketTrade(MarketOrder buyer, MarketOrder seller, int quantity, long price) {
        id = UuidV7.next(); state = TradeState.PENDING_SETTLEMENT; itemId = buyer.itemId;
        buyerOrderId = buyer.id; sellerOrderId = seller.id;
        buyerReservationKey = buyer.reservationKey; sellerReservationKey = seller.reservationKey;
        this.quantity = quantity; priceGoldPerItem = price;
    }
    TradeClaim claim() { return new TradeClaim(id, leaseId, itemId, buyerOrderId, sellerOrderId,
            buyerReservationKey, sellerReservationKey, quantity, priceGoldPerItem); }
}
