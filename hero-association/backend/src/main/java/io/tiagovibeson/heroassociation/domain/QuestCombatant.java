package io.tiagovibeson.heroassociation.domain;

import io.tiagovibeson.heroassociation.domain.combat.CombatTeam;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quest_combatant")
public class QuestCombatant extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combat_id", nullable = false)
    private QuestCombat combat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hero_id")
    private Hero hero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CombatTeam team;

    @Column(name = "formation_index", nullable = false)
    private int formationIndex;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "hero_class", length = 20)
    private HeroClass heroClass;

    @Column(name = "magic_level", nullable = false)
    private int magicLevel;

    @Column(name = "max_health", nullable = false)
    private int maxHealth;

    @Column(name = "current_health", nullable = false)
    private int currentHealth;

    @Column(name = "max_mana", nullable = false)
    private int maxMana;

    @Column(name = "current_mana", nullable = false)
    private int currentMana;

    @Column(name = "attack_damage", nullable = false)
    private int attackDamage;

    @Column(name = "attack_interval_milliseconds", nullable = false)
    private long attackIntervalMilliseconds;

    @Column(name = "health_recovery_per_second", nullable = false)
    private int healthRecoveryPerSecond;

    @Column(name = "mana_recovery_per_second", nullable = false)
    private int manaRecoveryPerSecond;

    @Column(name = "critical_chance", nullable = false)
    private double criticalChance;

    @Column(name = "critical_damage_multiplier", nullable = false)
    private double criticalDamageMultiplier;

    @Column(name = "next_basic_attack_at", nullable = false)
    private long nextBasicAttackAt;

    @Column(name = "fire_ball_next_cast_at")
    private Long fireBallNextCastAt;

    @Column(name = "lightning_rail_next_cast_at")
    private Long lightningRailNextCastAt;

    protected QuestCombatant() {
    }

    public Hero getHero() {
        return hero;
    }

    public CombatTeam getTeam() {
        return team;
    }

    public int getFormationIndex() {
        return formationIndex;
    }

    public String getName() {
        return name;
    }

    public HeroClass getHeroClass() {
        return heroClass;
    }

    public int getMagicLevel() {
        return magicLevel;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public int getMaxMana() {
        return maxMana;
    }

    public int getCurrentMana() {
        return currentMana;
    }

    public int getAttackDamage() {
        return attackDamage;
    }

    public long getAttackIntervalMilliseconds() {
        return attackIntervalMilliseconds;
    }

    public int getHealthRecoveryPerSecond() {
        return healthRecoveryPerSecond;
    }

    public int getManaRecoveryPerSecond() {
        return manaRecoveryPerSecond;
    }

    public double getCriticalChance() {
        return criticalChance;
    }

    public double getCriticalDamageMultiplier() {
        return criticalDamageMultiplier;
    }

    public long getNextBasicAttackAt() {
        return nextBasicAttackAt;
    }

    public Long getFireBallNextCastAt() {
        return fireBallNextCastAt;
    }

    public Long getLightningRailNextCastAt() {
        return lightningRailNextCastAt;
    }
}
