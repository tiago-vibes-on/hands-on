package io.tiagovibeson.heroassociation.domain.combat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Combatant {

    private final String id;
    private final String name;
    private final CombatTeam team;
    private final int maxHealth;
    private final int maxMana;
    private final int attackDamage;
    private final long attackIntervalMilliseconds;
    private final int healthRecoveryPerSecond;
    private final int manaRecoveryPerSecond;
    private final int magicLevel;
    private final double criticalChance;
    private final double criticalDamageMultiplier;
    private final List<CombatSpell> spells;
    private final Map<CombatSpell, Long> nextSpellCastAt = new EnumMap<>(CombatSpell.class);

    private int currentHealth;
    private int currentMana;
    private long nextBasicAttackAt;

    public Combatant(
            String id,
            String name,
            CombatTeam team,
            int maxHealth,
            int maxMana,
            int currentHealth,
            int currentMana,
            int attackDamage,
            long attackIntervalMilliseconds,
            int healthRecoveryPerSecond,
            int manaRecoveryPerSecond,
            int magicLevel,
            double criticalChance,
            double criticalDamageMultiplier,
            List<CombatSpell> spells) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.team = Objects.requireNonNull(team, "team must not be null");
        requirePositive(maxHealth, "maxHealth");
        requireNonNegative(maxMana, "maxMana");
        requireWithin(currentHealth, 0, maxHealth, "currentHealth");
        requireWithin(currentMana, 0, maxMana, "currentMana");
        requireNonNegative(attackDamage, "attackDamage");
        requirePositive(attackIntervalMilliseconds, "attackIntervalMilliseconds");
        requireNonNegative(healthRecoveryPerSecond, "healthRecoveryPerSecond");
        requireNonNegative(manaRecoveryPerSecond, "manaRecoveryPerSecond");
        requireNonNegative(magicLevel, "magicLevel");
        requireWithin(criticalChance, 0, 1, "criticalChance");
        if (criticalDamageMultiplier < 1) {
            throw new IllegalArgumentException("criticalDamageMultiplier must be at least 1");
        }

        this.maxHealth = maxHealth;
        this.maxMana = maxMana;
        this.currentHealth = currentHealth;
        this.currentMana = currentMana;
        this.attackDamage = attackDamage;
        this.attackIntervalMilliseconds = attackIntervalMilliseconds;
        this.healthRecoveryPerSecond = healthRecoveryPerSecond;
        this.manaRecoveryPerSecond = manaRecoveryPerSecond;
        this.magicLevel = magicLevel;
        this.criticalChance = criticalChance;
        this.criticalDamageMultiplier = criticalDamageMultiplier;
        this.spells = List.copyOf(spells);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CombatTeam getTeam() {
        return team;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public int getMaxMana() {
        return maxMana;
    }

    public int getCurrentHealth() {
        return currentHealth;
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

    public int getMagicLevel() {
        return magicLevel;
    }

    public double getCriticalChance() {
        return criticalChance;
    }

    public double getCriticalDamageMultiplier() {
        return criticalDamageMultiplier;
    }

    public boolean isAlive() {
        return currentHealth > 0;
    }

    boolean knows(CombatSpell spell) {
        return spells.contains(spell) && magicLevel >= spell.getRequiredMagicLevel();
    }

    long getNextBasicAttackAt() {
        return nextBasicAttackAt;
    }

    void scheduleBasicAttackAt(long timestamp) {
        nextBasicAttackAt = timestamp;
    }

    long getNextSpellCastAt(CombatSpell spell) {
        return nextSpellCastAt.getOrDefault(spell, Long.MAX_VALUE);
    }

    void scheduleSpellCastAt(CombatSpell spell, long timestamp) {
        nextSpellCastAt.put(spell, timestamp);
    }

    void spendMana(int amount) {
        currentMana -= amount;
    }

    boolean hasManaFor(CombatSpell spell) {
        return currentMana >= spell.getManaCost();
    }

    int recoverHealth() {
        int recovered = Math.min(healthRecoveryPerSecond, maxHealth - currentHealth);
        currentHealth += recovered;
        return recovered;
    }

    int recoverMana() {
        int recovered = Math.min(manaRecoveryPerSecond, maxMana - currentMana);
        currentMana += recovered;
        return recovered;
    }

    boolean takeDamage(int damage) {
        currentHealth = Math.max(0, currentHealth - damage);
        return currentHealth == 0;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static void requirePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    private static void requireWithin(double value, double minimum, double maximum, String fieldName) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(fieldName + " must be between " + minimum + " and " + maximum);
        }
    }
}
