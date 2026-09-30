package io.tiagovibeson.heroassociation.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "combat_battle_registration")
public class CombatBattleRegistration {

    @Id
    private UUID battleId;

    @Column(name = "party_id", nullable = false)
    private UUID partyId;

    @Column(name = "hero_bindings_json", nullable = false, columnDefinition = "text")
    private String heroBindingsJson;

    @Column(name = "next_sequence", nullable = false)
    private long nextSequence;

    @Column(name = "last_fact_at_milliseconds", nullable = false)
    private long lastFactAtMilliseconds;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected CombatBattleRegistration() {
    }

    public CombatBattleRegistration(UUID battleId, UUID partyId, String heroBindingsJson, Instant registeredAt) {
        this.battleId = battleId;
        this.partyId = partyId;
        this.heroBindingsJson = heroBindingsJson;
        this.nextSequence = 1;
        this.registeredAt = registeredAt;
    }

    public UUID getBattleId() {
        return battleId;
    }

    public UUID getPartyId() {
        return partyId;
    }

    public String getHeroBindingsJson() {
        return heroBindingsJson;
    }

    public long getNextSequence() {
        return nextSequence;
    }

    public long getLastFactAtMilliseconds() {
        return lastFactAtMilliseconds;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void advance(long lastSequence, long lastFactAtMilliseconds, boolean completed, Instant appliedAt) {
        if (completedAt != null || lastSequence < nextSequence || lastFactAtMilliseconds < this.lastFactAtMilliseconds) {
            throw new IllegalArgumentException("Combat facts cannot move the registered battle backward.");
        }
        nextSequence = Math.addExact(lastSequence, 1);
        this.lastFactAtMilliseconds = lastFactAtMilliseconds;
        if (completed) {
            completedAt = appliedAt;
        }
    }
}
