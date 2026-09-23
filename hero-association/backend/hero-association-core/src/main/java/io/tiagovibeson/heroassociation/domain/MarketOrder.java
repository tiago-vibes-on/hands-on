package io.tiagovibeson.heroassociation.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "market_order")
public class MarketOrder extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MarketOrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketOrderStatus status;

    @Column(name = "quantity_remaining", nullable = false)
    private int quantityRemaining;

    @Column(name = "price_gold_per_item", nullable = false)
    private long priceGoldPerItem;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MarketOrder() {
    }

    public MarketOrder(Agency agency, Item item, MarketOrderSide side, int quantity, long priceGoldPerItem) {
        this.agency = agency;
        this.item = item;
        this.side = side;
        status = MarketOrderStatus.OPEN;
        quantityRemaining = quantity;
        this.priceGoldPerItem = priceGoldPerItem;
        createdAt = Instant.now();
    }

    public Agency getAgency() {
        return agency;
    }

    public Item getItem() {
        return item;
    }

    public MarketOrderSide getSide() {
        return side;
    }

    public MarketOrderStatus getStatus() {
        return status;
    }

    public int getQuantityRemaining() {
        return quantityRemaining;
    }

    public long getPriceGoldPerItem() {
        return priceGoldPerItem;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void fill(int quantity) {
        if (quantity < 1 || quantity > quantityRemaining) {
            throw new IllegalArgumentException("Market order fill quantity is invalid.");
        }

        quantityRemaining -= quantity;
        status = quantityRemaining == 0 ? MarketOrderStatus.FILLED : MarketOrderStatus.PARTIALLY_FILLED;
    }

    public void cancel() {
        if (status == MarketOrderStatus.FILLED || status == MarketOrderStatus.CANCELLED) {
            throw new IllegalStateException("Only open market orders can be cancelled.");
        }

        status = MarketOrderStatus.CANCELLED;
    }
}
