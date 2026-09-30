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
@Table(name = "battle_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_battle_event_sequence",
                columnNames = { "battle_id", "sequence_number" }))
public class BattleEventRecord {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "battle_id", nullable = false)
    private BattleRecord battle;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(name = "event_json", nullable = false, columnDefinition = "text")
    private String eventJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BattleEventRecord() {
    }

    public BattleEventRecord(BattleRecord battle, long sequenceNumber, String eventJson, Instant createdAt) {
        this.id = UuidV7.next();
        this.battle = battle;
        this.sequenceNumber = sequenceNumber;
        this.eventJson = eventJson;
        this.createdAt = createdAt;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getEventJson() {
        return eventJson;
    }
}
