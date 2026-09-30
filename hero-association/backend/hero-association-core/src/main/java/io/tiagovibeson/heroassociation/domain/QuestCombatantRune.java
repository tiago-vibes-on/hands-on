package io.tiagovibeson.heroassociation.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "quest_combatant_rune",
        uniqueConstraints = @UniqueConstraint(columnNames = { "combatant_id", "slot_index" }))
public class QuestCombatantRune extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combatant_id", nullable = false)
    private QuestCombatant combatant;

    @Column(name = "rune_id", nullable = false)
    private UUID runeId;

    @Column(name = "slot_index", nullable = false)
    private int slotIndex;

    @Column(name = "rune_code", nullable = false, length = 50)
    private String runeCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RuneEffect effect;

    @Column(name = "effect_value", nullable = false)
    private double effectValue;

    protected QuestCombatantRune() {
    }

    static QuestCombatantRune from(QuestCombatant combatant, HeroRune slot) {
        QuestCombatantRune snapshot = new QuestCombatantRune();
        Rune rune = slot.getRune();
        snapshot.combatant = combatant;
        snapshot.runeId = rune.getId();
        snapshot.slotIndex = slot.getSlotIndex();
        snapshot.runeCode = rune.getCode();
        snapshot.effect = rune.getEffect();
        snapshot.effectValue = rune.getEffectValue();
        return snapshot;
    }

    public UUID getRuneId() {
        return runeId;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public String getRuneCode() {
        return runeCode;
    }

    public RuneEffect getEffect() {
        return effect;
    }

    public double getEffectValue() {
        return effectValue;
    }
}
