package io.tiagovibeson.heroassociation.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.combat.CombatTeam;
import io.tiagovibeson.heroassociation.domain.combat.CombatSpell;
import io.tiagovibeson.heroassociation.domain.combat.CombatantSnapshot;
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

@Entity
@Table(name = "quest_combatant")
public class QuestCombatant extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combat_id", nullable = false)
    private QuestCombat combat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hero_id")
    private Hero hero;

    @OneToMany(mappedBy = "combatant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("slotIndex")
    private List<QuestCombatantRune> runeSnapshots = new ArrayList<>();

    @Column(name = "creature_definition_id")
    private UUID creatureDefinitionId;

    @Column(name = "creature_definition_version")
    private Integer creatureDefinitionVersion;

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

    @Column(name = "hero_level")
    private Integer heroLevel;

    @Column(name = "melee_level")
    private Integer meleeLevel;

    @Column(name = "distance_level")
    private Integer distanceLevel;

    @Column(name = "shield_level")
    private Integer shieldLevel;

    @Column(name = "starting_stamina_milliseconds")
    private Long startingStaminaMilliseconds;

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

    @Column(name = "basic_attack_mana_cost", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private int basicAttackManaCost;

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
        combatant.heroLevel = hero.getLevel();
        combatant.meleeLevel = hero.getSkillLevel(HeroSkill.MELEE);
        combatant.distanceLevel = hero.getSkillLevel(HeroSkill.DISTANCE);
        combatant.shieldLevel = hero.getSkillLevel(HeroSkill.SHIELD);
        combatant.startingStaminaMilliseconds = hero.getStaminaMilliseconds();
        combatant.magicLevel = hero.getMagicLevel();
        combatant.baseExperience = 0;
        combatant.maxHealth = hero.getMaxHealth();
        combatant.currentHealth = hero.getCurrentHealth();
        combatant.maxMana = hero.getMaxMana();
        combatant.currentMana = hero.getCurrentMana();
        combatant.attackDamage = heroClass.getBaseAttackDamage();
        combatant.basicAttackManaCost = heroClass.getBasicAttackManaCost();
        combatant.attackIntervalMilliseconds = heroClass.getAttackIntervalMilliseconds();
        combatant.healthRecoveryPerSecond = heroClass.getHealthRecoveryPerSecond();
        combatant.manaRecoveryPerSecond = heroClass.getManaRecoveryPerSecond();
        hero.getRuneSlots().stream()
                .map(slot -> QuestCombatantRune.from(combatant, slot))
                .forEach(combatant.runeSnapshots::add);
        combatant.criticalChance = Math.min(1, runeEffectValue(combatant.runeSnapshots, RuneEffect.CRITICAL_CHANCE));
        combatant.criticalDamageMultiplier = 2 + runeEffectValue(combatant.runeSnapshots, RuneEffect.CRITICAL_DAMAGE);
        combatant.nextBasicAttackAt = 480 + (formationIndex * 170L);
        if (heroClass == HeroClass.MAGE && hero.getMagicLevel() >= CombatSpell.FIRE_BALL.getRequiredMagicLevel()) {
            combatant.fireBallNextCastAt = 900L;
        }
        if (heroClass == HeroClass.MAGE && hero.getMagicLevel() >= CombatSpell.LIGHTNING_RAIL.getRequiredMagicLevel()) {
            combatant.lightningRailNextCastAt = 1_350L;
        }
        return combatant;
    }

    static QuestCombatant forCreature(QuestCombat combat, CreatureCombatProfile definition, int formationIndex) {
        QuestCombatant combatant = new QuestCombatant();
        combatant.combat = combat;
        combatant.team = CombatTeam.CREATURES;
        combatant.formationIndex = formationIndex;
        combatant.name = definition.name();
        combatant.creatureDefinitionId = definition.definitionId();
        combatant.creatureDefinitionVersion = definition.version();
        combatant.magicLevel = 0;
        combatant.baseExperience = definition.baseExperience();
        combatant.maxHealth = definition.maxHealth();
        combatant.currentHealth = definition.maxHealth();
        combatant.maxMana = definition.maxMana();
        combatant.currentMana = definition.maxMana();
        combatant.attackDamage = definition.attackDamage();
        combatant.basicAttackManaCost = 0;
        combatant.attackIntervalMilliseconds = definition.attackIntervalMilliseconds();
        combatant.healthRecoveryPerSecond = definition.healthRecoveryPerSecond();
        combatant.manaRecoveryPerSecond = definition.manaRecoveryPerSecond();
        combatant.criticalChance = definition.criticalChance();
        combatant.criticalDamageMultiplier = definition.criticalDamageMultiplier();
        combatant.nextBasicAttackAt = 760 + (formationIndex * 160L);
        return combatant;
    }

    private static double runeEffectValue(List<QuestCombatantRune> runes, RuneEffect effect) {
        return runes.stream()
                .filter(rune -> rune.getEffect() == effect)
                .mapToDouble(QuestCombatantRune::getEffectValue)
                .sum();
    }

    public Hero getHero() {
        return hero;
    }

    public List<QuestCombatantRune> getRuneSnapshots() {
        return List.copyOf(runeSnapshots);
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

    public UUID getCreatureDefinitionId() {
        return creatureDefinitionId;
    }

    public Integer getCreatureDefinitionVersion() {
        return creatureDefinitionVersion;
    }

    public HeroClass getHeroClass() {
        return heroClass;
    }

    public int getMagicLevel() {
        return magicLevel;
    }

    public Integer getHeroLevel() {
        return heroLevel;
    }

    public Integer getMeleeLevel() {
        return meleeLevel;
    }

    public Integer getDistanceLevel() {
        return distanceLevel;
    }

    public Integer getShieldLevel() {
        return shieldLevel;
    }

    public Long getStartingStaminaMilliseconds() {
        return startingStaminaMilliseconds;
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

    public int getBasicAttackManaCost() {
        return basicAttackManaCost;
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
