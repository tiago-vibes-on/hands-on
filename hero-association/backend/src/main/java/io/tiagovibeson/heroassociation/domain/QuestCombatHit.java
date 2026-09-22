package io.tiagovibeson.heroassociation.domain;

import java.util.Map;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.combat.CombatHit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quest_combat_hit")
public class QuestCombatHit extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private QuestCombatEvent combatEvent;

    @Column(name = "hit_index", nullable = false)
    private int hitIndex;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    private QuestCombatant target;

    @Column(nullable = false)
    private int damage;

    @Column(nullable = false)
    private boolean critical;

    @Column(nullable = false)
    private boolean defeated;

    protected QuestCombatHit() {
    }

    QuestCombatHit(
            QuestCombatEvent combatEvent,
            int hitIndex,
            CombatHit hit,
            Map<UUID, QuestCombatant> combatantsById) {
        this.combatEvent = combatEvent;
        this.hitIndex = hitIndex;
        target = combatantFor(hit.targetId(), combatantsById);
        damage = hit.damage();
        critical = hit.critical();
        defeated = hit.defeated();
    }

    private static QuestCombatant combatantFor(String id, Map<UUID, QuestCombatant> combatantsById) {
        UUID combatantId = UUID.fromString(id);
        QuestCombatant combatant = combatantsById.get(combatantId);
        if (combatant == null) {
            throw new IllegalArgumentException("Combat hit contains an unknown combatant: " + combatantId);
        }
        return combatant;
    }

    public QuestCombatant getTarget() {
        return target;
    }

    public int getDamage() {
        return damage;
    }

    public boolean isCritical() {
        return critical;
    }

    public boolean isDefeated() {
        return defeated;
    }
}
