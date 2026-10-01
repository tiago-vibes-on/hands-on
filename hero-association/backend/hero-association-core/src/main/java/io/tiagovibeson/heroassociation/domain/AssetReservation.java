package io.tiagovibeson.heroassociation.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "asset_reservation", uniqueConstraints = @UniqueConstraint(columnNames = "reservation_key"))
public class AssetReservation extends UuidEntity {

    @Column(name = "reservation_key", nullable = false, updatable = false)
    private UUID reservationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, updatable = false, length = 10)
    private AssetOwnerType ownerType;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, updatable = false, length = 10)
    private AssetResourceType resourceType;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "initial_quantity", nullable = false, updatable = false)
    private int initialQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    @Column(name = "unit_price_gold_per_item", nullable = false, updatable = false)
    private long unitPriceGoldPerItem;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssetReservation() {
    }

    public AssetReservation(UUID reservationKey, AssetOwnerType ownerType, UUID ownerId,
            AssetResourceType resourceType, UUID itemId, int quantity, long unitPriceGoldPerItem) {
        this.reservationKey = reservationKey;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.resourceType = resourceType;
        this.itemId = itemId;
        initialQuantity = quantity;
        remainingQuantity = quantity;
        this.unitPriceGoldPerItem = unitPriceGoldPerItem;
        createdAt = Instant.now();
    }

    public UUID getReservationKey() {
        return reservationKey;
    }

    public AssetOwnerType getOwnerType() {
        return ownerType;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public AssetResourceType getResourceType() {
        return resourceType;
    }

    public UUID getItemId() {
        return itemId;
    }

    public int getInitialQuantity() {
        return initialQuantity;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    public long getUnitPriceGoldPerItem() {
        return unitPriceGoldPerItem;
    }

    public void consume(int quantity) {
        if (quantity <= 0 || quantity > remainingQuantity) {
            throw new IllegalArgumentException("Reservation quantity is unavailable.");
        }
        remainingQuantity -= quantity;
    }
}
