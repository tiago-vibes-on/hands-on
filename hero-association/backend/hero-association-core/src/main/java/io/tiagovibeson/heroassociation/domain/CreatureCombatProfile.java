package io.tiagovibeson.heroassociation.domain;

import java.util.UUID;

/** Immutable creature inputs copied into a battle, safe to cache without a Hibernate session. */
public record CreatureCombatProfile(
        UUID definitionId,
        String name,
        int version,
        int baseExperience,
        int maxHealth,
        int maxMana,
        int attackDamage,
        long attackIntervalMilliseconds,
        int healthRecoveryPerSecond,
        int manaRecoveryPerSecond,
        double criticalChance,
        double criticalDamageMultiplier) {

    public static CreatureCombatProfile from(CreatureDefinition definition) {
        return new CreatureCombatProfile(
                definition.getId(),
                definition.getName(),
                definition.getVersion(),
                definition.getBaseExperience(),
                definition.getMaxHealth(),
                definition.getMaxMana(),
                definition.getAttackDamage(),
                definition.getAttackIntervalMilliseconds(),
                definition.getHealthRecoveryPerSecond(),
                definition.getManaRecoveryPerSecond(),
                definition.getCriticalChance(),
                definition.getCriticalDamageMultiplier());
    }
}
