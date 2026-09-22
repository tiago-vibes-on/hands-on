package io.tiagovibeson.heroassociation.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.combat.CombatAction;
import io.tiagovibeson.heroassociation.domain.combat.CombatEvent;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "quest_combat_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_quest_combat_event_combat_sequence",
                columnNames = { "combat_id", "sequence_number" }))
public class QuestCombatEvent extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combat_id", nullable = false)
    private QuestCombat combat;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(name = "occurred_at_milliseconds", nullable = false)
    private long occurredAtMilliseconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CombatAction action;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private QuestCombatant actor;

    @Column(name = "mana_spent", nullable = false)
    private int manaSpent;

    @Column(name = "health_recovered", nullable = false)
    private int healthRecovered;

    @Column(name = "mana_recovered", nullable = false)
    private int manaRecovered;

    @OneToMany(mappedBy = "combatEvent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("hitIndex")
    private List<QuestCombatHit> hits = new ArrayList<>();

    protected QuestCombatEvent() {
    }

    private QuestCombatEvent(
            QuestCombat combat,
            long sequenceNumber,
            CombatEvent event,
            Map<UUID, QuestCombatant> combatantsById) {
        this.combat = combat;
        this.sequenceNumber = sequenceNumber;
        occurredAtMilliseconds = event.occurredAtMilliseconds();
        action = event.action();
        actor = combatantFor(event.actorId(), combatantsById);
        manaSpent = event.manaSpent();
        healthRecovered = event.healthRecovered();
        manaRecovered = event.manaRecovered();

        for (int hitIndex = 0; hitIndex < event.hits().size(); hitIndex++) {
            hits.add(new QuestCombatHit(this, hitIndex, event.hits().get(hitIndex), combatantsById));
        }
    }

    static QuestCombatEvent from(
            QuestCombat combat,
            long sequenceNumber,
            CombatEvent event,
            Map<UUID, QuestCombatant> combatantsById) {
        return new QuestCombatEvent(combat, sequenceNumber, event, combatantsById);
    }

    private static QuestCombatant combatantFor(String id, Map<UUID, QuestCombatant> combatantsById) {
        UUID combatantId = UUID.fromString(id);
        QuestCombatant combatant = combatantsById.get(combatantId);
        if (combatant == null) {
            throw new IllegalArgumentException("Combat event contains an unknown combatant: " + combatantId);
        }
        return combatant;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public long getOccurredAtMilliseconds() {
        return occurredAtMilliseconds;
    }

    public CombatAction getAction() {
        return action;
    }

    public QuestCombatant getActor() {
        return actor;
    }

    public int getManaSpent() {
        return manaSpent;
    }

    public int getHealthRecovered() {
        return healthRecovered;
    }

    public int getManaRecovered() {
        return manaRecovered;
    }

    public List<QuestCombatHit> getHits() {
        return hits;
    }
}
