package io.tiagovibeson.heroassociation.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Core's durable admission and exactly-once settlement cursor. */
@Entity
@Table(name = "expedition_reservation")
public class ExpeditionReservation {

    @Id
    private UUID expeditionId;

    @Column(nullable = false)
    private UUID ownerManagerId;

    @Column(nullable = false)
    private UUID agencyId;

    @Column(nullable = false)
    private UUID partyId;

    @Column(nullable = false, columnDefinition = "text")
    private String baselineJson;

    @Column(columnDefinition = "text", updatable = false)
    private String worldPlanJson;

    public String getWorldPlanJson() { return worldPlanJson; }
    public void pinWorldPlan(String plan) {
        if (worldPlanJson != null) throw new IllegalStateException("Map plan is already pinned.");
        worldPlanJson = java.util.Objects.requireNonNull(plan);
    }

    @Column(nullable = false)
    private Instant reservedAt;

    @Column(length = 64)
    private String settlementDigest;

    private Instant appliedAt;

    private Instant releasedAt;

    @Column(nullable = false, updatable = false)
    private UUID assetsAdmissionKey;
    @Column(nullable = false, updatable = false)
    private UUID assetsSettlementKey;
    @Column(nullable = false)
    private boolean assetsSnapshotConfirmed;
    public UUID getAssetsAdmissionKey() { return assetsAdmissionKey; }
    public UUID getAssetsSettlementKey() { return assetsSettlementKey; }
    public boolean isAssetsSnapshotConfirmed() { return assetsSnapshotConfirmed; }
    public void confirmAssetsSnapshot(String baseline) {
        if (appliedAt != null || releasedAt != null) throw new IllegalStateException("Reservation is no longer active.");
        baselineJson = baseline; assetsSnapshotConfirmed = true;
    }

    protected ExpeditionReservation() {
    }

    public ExpeditionReservation(UUID expeditionId, UUID ownerManagerId, UUID agencyId,
                                 UUID partyId, String baselineJson, Instant reservedAt) {
        this.expeditionId = expeditionId;
        assetsAdmissionKey = UuidV7.next(); assetsSettlementKey = UuidV7.next();
        this.ownerManagerId = ownerManagerId;
        this.agencyId = agencyId;
        this.partyId = partyId;
        this.baselineJson = baselineJson;
        this.reservedAt = reservedAt;
    }

    public UUID getExpeditionId() { return expeditionId; }
    public UUID getOwnerManagerId() { return ownerManagerId; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getPartyId() { return partyId; }
    public String getBaselineJson() { return baselineJson; }
    public String getSettlementDigest() { return settlementDigest; }
    public Instant getAppliedAt() { return appliedAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public Instant getReservedAt() { return reservedAt; }

    public void markReleased(Instant when) {
        if (appliedAt != null || releasedAt != null) {
            throw new IllegalStateException("Only an active reservation can be released.");
        }
        releasedAt = java.util.Objects.requireNonNull(when);
    }

    public void markApplied(String digest, Instant when) {
        if (appliedAt != null || releasedAt != null || digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("Expedition reservation already settled or digest invalid.");
        }
        settlementDigest = digest;
        appliedAt = when;
    }
}
