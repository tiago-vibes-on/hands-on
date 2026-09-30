package io.tiagovibeson.heroassociation.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "combat_progression_inbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_combat_progression_inbox_battle_sequence",
                columnNames = { "battle_id", "first_sequence" }),
        indexes = @Index(name = "ix_combat_progression_inbox_unapplied",
                columnList = "applied_at, battle_id, first_sequence"))
public class CombatProgressionInboxRecord {

    @Id
    private UUID id;

    @Column(name = "battle_id", nullable = false)
    private UUID battleId;

    @Column(name = "first_sequence", nullable = false)
    private long firstSequence;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    protected CombatProgressionInboxRecord() {
    }

    public CombatProgressionInboxRecord(
            UUID id, UUID battleId, long firstSequence, long lastSequence, String payloadJson, Instant receivedAt) {
        this.id = id;
        this.battleId = battleId;
        this.firstSequence = firstSequence;
        this.lastSequence = lastSequence;
        this.payloadJson = payloadJson;
        this.receivedAt = receivedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBattleId() {
        return battleId;
    }

    public long getFirstSequence() {
        return firstSequence;
    }

    public long getLastSequence() {
        return lastSequence;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void markApplied(Instant appliedAt) {
        if (this.appliedAt != null) {
            throw new IllegalStateException("Combat batch is already applied.");
        }
        this.appliedAt = appliedAt;
    }
}
