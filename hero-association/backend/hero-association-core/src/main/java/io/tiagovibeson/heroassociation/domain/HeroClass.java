package io.tiagovibeson.heroassociation.domain;

import java.math.BigDecimal;

public enum HeroClass {
    WARRIOR(300, 40, 50, 10, 10, 2, 22, 1_300),
    MAGE(100, 15, 500, 60, 2, 10, 32, 1_700),
    ARCHER(200, 25, 200, 25, 6, 6, 26, 1_100);

    private final int baseHealth;
    private final int healthGainPerLevel;
    private final int baseMana;
    private final int manaGainPerLevel;
    private final int healthRecoveryPerSecond;
    private final int manaRecoveryPerSecond;
    private final int baseAttackDamage;
    private final long attackIntervalMilliseconds;

    HeroClass(
            int baseHealth,
            int healthGainPerLevel,
            int baseMana,
            int manaGainPerLevel,
            int healthRecoveryPerSecond,
            int manaRecoveryPerSecond,
            int baseAttackDamage,
            long attackIntervalMilliseconds) {
        this.baseHealth = baseHealth;
        this.healthGainPerLevel = healthGainPerLevel;
        this.baseMana = baseMana;
        this.manaGainPerLevel = manaGainPerLevel;
        this.healthRecoveryPerSecond = healthRecoveryPerSecond;
        this.manaRecoveryPerSecond = manaRecoveryPerSecond;
        this.baseAttackDamage = baseAttackDamage;
        this.attackIntervalMilliseconds = attackIntervalMilliseconds;
    }

    public int getBaseHealth() {
        return baseHealth;
    }

    public int getBaseMana() {
        return baseMana;
    }

    public int getHealthGainPerLevel() {
        return healthGainPerLevel;
    }

    public int getManaGainPerLevel() {
        return manaGainPerLevel;
    }

    public BigDecimal getSkillAptitude(HeroSkill skill) {
        return switch (skill) {
            case MELEE -> switch (this) {
                case WARRIOR -> BigDecimal.ONE;
                case ARCHER -> new BigDecimal("0.25");
                case MAGE -> new BigDecimal("0.05");
            };
            case DISTANCE -> switch (this) {
                case WARRIOR -> new BigDecimal("0.25");
                case ARCHER -> BigDecimal.ONE;
                case MAGE -> new BigDecimal("0.05");
            };
            case MAGIC -> switch (this) {
                case WARRIOR -> new BigDecimal("0.05");
                case ARCHER -> new BigDecimal("0.25");
                case MAGE -> BigDecimal.ONE;
            };
            case SHIELD -> throw new IllegalArgumentException("Shield aptitude is not defined yet.");
        };
    }

    public int getHealthRecoveryPerSecond() {
        return healthRecoveryPerSecond;
    }

    public int getManaRecoveryPerSecond() {
        return manaRecoveryPerSecond;
    }

    public int getBaseAttackDamage() {
        return baseAttackDamage;
    }

    public long getAttackIntervalMilliseconds() {
        return attackIntervalMilliseconds;
    }
}
