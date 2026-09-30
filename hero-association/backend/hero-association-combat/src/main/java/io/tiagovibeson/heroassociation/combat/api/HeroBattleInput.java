package io.tiagovibeson.heroassociation.combat.api;

import java.util.List;
import java.util.UUID;

public record HeroBattleInput(
        UUID combatantId,
        UUID heroId,
        HeroBattleClass heroClass,
        int level,
        int meleeLevel,
        int distanceLevel,
        int shieldLevel,
        int magicLevel,
        long startingStaminaMilliseconds,
        List<RuneBattleInput> runeSlots) {
}
