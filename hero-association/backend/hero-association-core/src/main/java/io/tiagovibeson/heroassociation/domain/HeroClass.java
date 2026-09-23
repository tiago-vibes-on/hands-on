package io.tiagovibeson.heroassociation.domain;

public enum HeroClass {
    WARRIOR(300, 50, 10, 2, 22, 1_300),
    MAGE(100, 500, 2, 10, 32, 1_700),
    ARCHER(200, 200, 6, 6, 26, 1_100);

    private final int baseHealth;
    private final int baseMana;
    private final int healthRecoveryPerSecond;
    private final int manaRecoveryPerSecond;
    private final int baseAttackDamage;
    private final long attackIntervalMilliseconds;

    HeroClass(
            int baseHealth,
            int baseMana,
            int healthRecoveryPerSecond,
            int manaRecoveryPerSecond,
            int baseAttackDamage,
            long attackIntervalMilliseconds) {
        this.baseHealth = baseHealth;
        this.baseMana = baseMana;
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
