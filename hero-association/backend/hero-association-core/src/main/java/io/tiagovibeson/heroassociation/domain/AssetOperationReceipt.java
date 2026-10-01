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
@Table(name = "asset_operation_receipt", uniqueConstraints = @UniqueConstraint(columnNames = "operation_key"))
public class AssetOperationReceipt extends UuidEntity {

    @Column(name = "operation_key", nullable = false, updatable = false)
    private UUID operationKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AssetOperationKind kind;

    @Column(name = "first_reservation_key", nullable = false, updatable = false)
    private UUID firstReservationKey;

    @Column(name = "second_reservation_key", updatable = false)
    private UUID secondReservationKey;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "execution_price_gold_per_item", nullable = false, updatable = false)
    private long executionPriceGoldPerItem;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssetOperationReceipt() {
    }

    public AssetOperationReceipt(UUID operationKey, AssetOperationKind kind,
            UUID firstReservationKey, UUID secondReservationKey, int quantity,
            long executionPriceGoldPerItem) {
        this.operationKey = operationKey;
        this.kind = kind;
        this.firstReservationKey = firstReservationKey;
        this.secondReservationKey = secondReservationKey;
        this.quantity = quantity;
        this.executionPriceGoldPerItem = executionPriceGoldPerItem;
        createdAt = Instant.now();
    }

    public UUID getOperationKey() {
        return operationKey;
    }

    public AssetOperationKind getKind() {
        return kind;
    }

    public UUID getFirstReservationKey() {
        return firstReservationKey;
    }

    public UUID getSecondReservationKey() {
        return secondReservationKey;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getExecutionPriceGoldPerItem() {
        return executionPriceGoldPerItem;
    }
}
