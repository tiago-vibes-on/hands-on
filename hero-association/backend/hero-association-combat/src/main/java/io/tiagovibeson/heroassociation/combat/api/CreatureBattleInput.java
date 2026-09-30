package io.tiagovibeson.heroassociation.combat.api;

import java.util.UUID;

public record CreatureBattleInput(
        UUID combatantId,
        UUID definitionId,
        int definitionVersion,
        int baseExperience) {
}
