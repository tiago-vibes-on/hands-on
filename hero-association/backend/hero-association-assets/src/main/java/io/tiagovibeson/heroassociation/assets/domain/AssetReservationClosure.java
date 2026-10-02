package io.tiagovibeson.heroassociation.assets.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Permanent fence: a late placement retry cannot reserve after abandonment. */
@Entity
@Table(name = "asset_reservation_closure", uniqueConstraints = @UniqueConstraint(columnNames = "reservation_key"))
public class AssetReservationClosure extends UuidEntity {
    @Column(name = "reservation_key", nullable = false, updatable = false)
    private UUID reservationKey;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssetReservationClosure() { }

    public AssetReservationClosure(UUID reservationKey) {
        this.reservationKey = reservationKey;
        createdAt = Instant.now();
    }
}
