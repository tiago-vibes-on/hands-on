package io.tiagovibeson.heroassociation.market;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

@MappedSuperclass
public abstract class RecoveryRecord {
    @Id @Column(nullable = false, updatable = false) public UUID id;
    @Version public long version;
    @Column(name = "created_at", nullable = false, updatable = false) public Instant createdAt = Instant.now();
    @Column(name = "lease_id") public UUID leaseId;
    @Column(name = "lease_until") public Instant leaseUntil;
    @Column(nullable = false) public int attempts;
    @Column(name = "next_attempt_at", nullable = false) public Instant nextAttemptAt = Instant.EPOCH;
    @Column(name = "last_error", length = 255) public String lastError;
}
