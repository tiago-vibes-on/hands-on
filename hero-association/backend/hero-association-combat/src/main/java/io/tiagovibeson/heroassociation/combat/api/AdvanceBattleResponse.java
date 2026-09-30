package io.tiagovibeson.heroassociation.combat.api;

import java.util.UUID;

public record AdvanceBattleResponse(
        UUID battleId,
        String status,
        long currentTimeMilliseconds,
        long lastEventSequence,
        long lastFactSequence,
        boolean replayed) {
}
