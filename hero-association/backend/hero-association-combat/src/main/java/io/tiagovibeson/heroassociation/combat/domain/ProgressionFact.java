package io.tiagovibeson.heroassociation.combat.domain;

import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.combat.CombatAction;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattleSnapshot;

public record ProgressionFact(
        long sequence,
        long occurredAtMilliseconds,
        ProgressionFactType type,
        List<UUID> heroIds,
        UUID heroId,
        UUID creatureCombatantId,
        long elapsedMilliseconds,
        CombatAction action,
        int manaSpent,
        int baseExperience,
        CombatBattleSnapshot finalSnapshot) {

    public ProgressionFact {
        heroIds = List.copyOf(heroIds);
    }
}
