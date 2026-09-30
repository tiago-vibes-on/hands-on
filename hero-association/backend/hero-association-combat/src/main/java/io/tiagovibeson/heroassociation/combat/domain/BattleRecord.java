package io.tiagovibeson.heroassociation.combat.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "battle")
public class BattleRecord {

    @Column(nullable = false, length = 20)
    private String status;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "request_json", nullable = false, columnDefinition = "text")
    private String requestJson;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
    private String snapshotJson;

    @Column(name = "next_event_sequence", nullable = false)
    private long nextEventSequence;

    @Column(name = "next_fact_sequence", nullable = false)
    private long nextFactSequence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_advanced_at", nullable = false)
    private Instant lastAdvancedAt;

    protected BattleRecord() {
    }

    public UUID getId() {
        return id;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAdvancedAt() {
        return lastAdvancedAt;
    }

    public long getNextEventSequence() {
        return nextEventSequence;
    }

    public long getNextFactSequence() {
        return nextFactSequence;
    }

    public void advance(
            String snapshotJson, String status, long nextEventSequence, long nextFactSequence, Instant advancedAt) {
        this.snapshotJson = snapshotJson;
        this.status = status;
        this.nextEventSequence = nextEventSequence;
        this.nextFactSequence = nextFactSequence;
        this.lastAdvancedAt = advancedAt;
    }
}
