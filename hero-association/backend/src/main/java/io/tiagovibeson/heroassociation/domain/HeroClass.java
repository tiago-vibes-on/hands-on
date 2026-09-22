package io.tiagovibeson.heroassociation.domain;

public enum HeroClass {
    WARRIOR(300, 50, 10, 2),
    MAGE(100, 500, 2, 10),
    ARCHER(200, 200, 6, 6);

    private final int baseHealth;
    private final int baseMana;
    private final int healthRecoveryPerSecond;
    private final int manaRecoveryPerSecond;

    HeroClass(int baseHealth, int baseMana, int healthRecoveryPerSecond, int manaRecoveryPerSecond) {
        this.baseHealth = baseHealth;
        this.baseMana = baseMana;
        this.healthRecoveryPerSecond = healthRecoveryPerSecond;
        this.manaRecoveryPerSecond = manaRecoveryPerSecond;
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
}
