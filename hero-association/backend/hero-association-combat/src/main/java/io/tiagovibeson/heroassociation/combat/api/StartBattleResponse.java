package io.tiagovibeson.heroassociation.combat.api;

import java.time.Instant;
import java.util.UUID;

public record StartBattleResponse(
        UUID battleId,
        String status,
        Instant createdAt,
        boolean replayed) {
}
