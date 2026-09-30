package io.tiagovibeson.heroassociation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Versioned creature balancing data; new versions affect only new battles. */
@Entity
@Table(name = "creature_definition", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "version" }))
public class CreatureDefinition extends UuidEntity {
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false)
    private int version;
    @Column(name = "base_experience", nullable = false)
    private int baseExperience;
    @Column(name = "max_health", nullable = false)
    private int maxHealth;
    @Column(name = "max_mana", nullable = false)
    private int maxMana;
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

    protected CreatureDefinition() {
    }

    public String getName() { return name; }
    public int getVersion() { return version; }
    public int getBaseExperience() { return baseExperience; }
    public int getMaxHealth() { return maxHealth; }
    public int getMaxMana() { return maxMana; }
    public int getAttackDamage() { return attackDamage; }
    public long getAttackIntervalMilliseconds() { return attackIntervalMilliseconds; }
    public int getHealthRecoveryPerSecond() { return healthRecoveryPerSecond; }
    public int getManaRecoveryPerSecond() { return manaRecoveryPerSecond; }
    public double getCriticalChance() { return criticalChance; }
    public double getCriticalDamageMultiplier() { return criticalDamageMultiplier; }
}
