package io.tiagovibeson.heroassociation.combat.api;

import java.util.UUID;

public record RuneBattleInput(
        UUID runeId,
        int slotIndex,
        String code,
        RuneBattleEffect effect,
        double effectValue) {
}
