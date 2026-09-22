package io.tiagovibeson.heroassociation.domain.combat;

import java.util.List;
import java.util.Map;

public record CombatantSnapshot(
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
        List<CombatSpell> spells,
        long nextBasicAttackAt,
        Map<CombatSpell, Long> nextSpellCastAt) {

    public CombatantSnapshot {
        spells = List.copyOf(spells);
        nextSpellCastAt = Map.copyOf(nextSpellCastAt);
    }
}
