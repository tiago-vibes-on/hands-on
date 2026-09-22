package io.tiagovibeson.heroassociation.domain.combat;

public enum CombatSpell {
    FIRE_BALL(CombatAction.FIRE_BALL, 10, 1.5, 10, 20, 3_000, false),
    LIGHTNING_RAIL(CombatAction.LIGHTNING_RAIL, 2, 0.8, 15, 40, 5_000, true);

    private final CombatAction action;
    private final int baseDamage;
    private final double magicLevelScaling;
    private final int requiredMagicLevel;
    private final int manaCost;
    private final long cooldownMilliseconds;
    private final boolean affectsAllOpponents;

    CombatSpell(
            CombatAction action,
            int baseDamage,
            double magicLevelScaling,
            int requiredMagicLevel,
            int manaCost,
            long cooldownMilliseconds,
            boolean affectsAllOpponents) {
        this.action = action;
        this.baseDamage = baseDamage;
        this.magicLevelScaling = magicLevelScaling;
        this.requiredMagicLevel = requiredMagicLevel;
        this.manaCost = manaCost;
        this.cooldownMilliseconds = cooldownMilliseconds;
        this.affectsAllOpponents = affectsAllOpponents;
    }

    public CombatAction getAction() {
        return action;
    }

    public int getRequiredMagicLevel() {
        return requiredMagicLevel;
    }

    public int getManaCost() {
        return manaCost;
    }

    public long getCooldownMilliseconds() {
        return cooldownMilliseconds;
    }

    public boolean affectsAllOpponents() {
        return affectsAllOpponents;
    }

    public int damageForMagicLevel(int magicLevel) {
        return (int) Math.round(baseDamage + (magicLevel * magicLevelScaling));
    }
}
