package io.tiagovibeson.heroassociation.combat.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "battle_progression_batch",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_battle_progression_first_sequence",
                columnNames = { "battle_id", "first_sequence" }))
public class BattleProgressionBatchRecord {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "battle_id", nullable = false)
    private BattleRecord battle;

    @Column(name = "first_sequence", nullable = false)
    private long firstSequence;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "facts_json", nullable = false, columnDefinition = "text")
    private String factsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected BattleProgressionBatchRecord() {
    }

    public BattleProgressionBatchRecord(
            BattleRecord battle, long firstSequence, long lastSequence, String factsJson, Instant createdAt) {
        this.id = UuidV7.next();
        this.battle = battle;
        this.firstSequence = firstSequence;
        this.lastSequence = lastSequence;
        this.factsJson = factsJson;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBattleId() {
        return battle.getId();
    }

    public long getFirstSequence() {
        return firstSequence;
    }

    public long getLastSequence() {
        return lastSequence;
    }

    public String getFactsJson() {
        return factsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void markPublished(Instant publishedAt) {
        if (this.publishedAt != null) {
            throw new IllegalStateException("Progression batch was already published.");
        }
        this.publishedAt = publishedAt;
    }
}
