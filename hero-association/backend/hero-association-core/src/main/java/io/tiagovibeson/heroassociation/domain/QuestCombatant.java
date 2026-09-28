package io.tiagovibeson.heroassociation.domain;

import java.time.Instant;

import io.tiagovibeson.heroassociation.domain.combat.CombatTeam;
import io.tiagovibeson.heroassociation.domain.combat.CombatSpell;
import io.tiagovibeson.heroassociation.domain.combat.CombatantSnapshot;
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

    @Column(name = "base_experience", nullable = false)
    private int baseExperience;

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

    static QuestCombatant forHero(QuestCombat combat, Hero hero, int formationIndex) {
        HeroClass heroClass = hero.getHeroClass();
        QuestCombatant combatant = new QuestCombatant();
        combatant.combat = combat;
        combatant.hero = hero;
        combatant.team = CombatTeam.HEROES;
        combatant.formationIndex = formationIndex;
        combatant.name = hero.getAlias();
        combatant.heroClass = heroClass;
        combatant.magicLevel = hero.getMagicLevel();
        combatant.baseExperience = 0;
        combatant.maxHealth = hero.getMaxHealth();
        combatant.currentHealth = hero.getCurrentHealth();
        combatant.maxMana = hero.getMaxMana();
        combatant.currentMana = hero.getCurrentMana();
        combatant.attackDamage = heroClass.getBaseAttackDamage();
        combatant.attackIntervalMilliseconds = heroClass.getAttackIntervalMilliseconds();
        combatant.healthRecoveryPerSecond = heroClass.getHealthRecoveryPerSecond();
        combatant.manaRecoveryPerSecond = heroClass.getManaRecoveryPerSecond();
        combatant.criticalChance = Math.min(1, runeEffectValue(hero, RuneEffect.CRITICAL_CHANCE));
        combatant.criticalDamageMultiplier = 2 + runeEffectValue(hero, RuneEffect.CRITICAL_DAMAGE);
        combatant.nextBasicAttackAt = 480 + (formationIndex * 170L);
        if (heroClass == HeroClass.MAGE && hero.getMagicLevel() >= CombatSpell.FIRE_BALL.getRequiredMagicLevel()) {
            combatant.fireBallNextCastAt = 900L;
        }
        if (heroClass == HeroClass.MAGE && hero.getMagicLevel() >= CombatSpell.LIGHTNING_RAIL.getRequiredMagicLevel()) {
            combatant.lightningRailNextCastAt = 1_350L;
        }
        return combatant;
    }

    static QuestCombatant forCreature(QuestCombat combat, String name, int formationIndex) {
        QuestCombatant combatant = new QuestCombatant();
        combatant.combat = combat;
        combatant.team = CombatTeam.CREATURES;
        combatant.formationIndex = formationIndex;
        combatant.name = name;
        combatant.magicLevel = 0;
        combatant.baseExperience = 100;
        combatant.maxHealth = 120;
        combatant.currentHealth = 120;
        combatant.maxMana = 100;
        combatant.currentMana = 100;
        combatant.attackDamage = 10;
        combatant.attackIntervalMilliseconds = 1_600;
        combatant.healthRecoveryPerSecond = 0;
        combatant.manaRecoveryPerSecond = 0;
        combatant.criticalChance = 0;
        combatant.criticalDamageMultiplier = 2;
        combatant.nextBasicAttackAt = 760 + (formationIndex * 160L);
        return combatant;
    }

    private static double runeEffectValue(Hero hero, RuneEffect effect) {
        return hero.getRuneSlots().stream()
                .map(HeroRune::getRune)
                .filter(rune -> rune.getEffect() == effect)
                .mapToDouble(Rune::getEffectValue)
                .sum();
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

    public int getBaseExperience() {
        return baseExperience;
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

    void apply(CombatantSnapshot snapshot, Instant synchronizedAt) {
        currentHealth = snapshot.currentHealth();
        currentMana = snapshot.currentMana();
        nextBasicAttackAt = snapshot.nextBasicAttackAt();
        fireBallNextCastAt = snapshot.nextSpellCastAt().get(CombatSpell.FIRE_BALL);
        lightningRailNextCastAt = snapshot.nextSpellCastAt().get(CombatSpell.LIGHTNING_RAIL);
        if (hero != null) {
            hero.synchronizeCombatResources(currentHealth, currentMana, synchronizedAt);
        }
    }
}
