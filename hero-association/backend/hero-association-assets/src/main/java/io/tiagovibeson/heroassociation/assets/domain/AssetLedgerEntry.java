package io.tiagovibeson.heroassociation.assets.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

/** Append-only available-resource posting; reservations and receipts describe held resources. */
@Entity @Table(name = "asset_ledger_entry")
public class AssetLedgerEntry extends UuidEntity {
    @Column(nullable = false, updatable = false) public UUID operationKey;
    @Column(nullable = false, updatable = false, length = 10) public String ownerType;
    @Column(nullable = false, updatable = false) public UUID ownerId;
    @Column(nullable = false, updatable = false, length = 30) public String balanceScope;
    @Column(nullable = false, updatable = false, length = 10) public String resourceType;
    @Column(updatable = false) public UUID resourceId;
    @Column(nullable = false, updatable = false) public long balanceBefore;
    @Column(nullable = false, updatable = false) public long balanceAfter;
    @Column(nullable = false, updatable = false) public Instant createdAt;
    protected AssetLedgerEntry() { }
    public AssetLedgerEntry(UUID key, String ownerType, UUID owner, String scope, String type, UUID resource, long before, long after) {
        operationKey = key; this.ownerType = ownerType; ownerId = owner; balanceScope = scope;
        resourceType = type; resourceId = resource; balanceBefore = before; balanceAfter = after; createdAt = Instant.now();
    }
}
