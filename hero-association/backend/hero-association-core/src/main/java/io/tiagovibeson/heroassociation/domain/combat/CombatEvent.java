package io.tiagovibeson.heroassociation.domain.combat;

import java.util.List;

public record CombatEvent(
        long occurredAtMilliseconds,
        CombatAction action,
        String actorId,
        List<CombatHit> hits,
        int manaSpent,
        int healthRecovered,
        int manaRecovered) {

    public CombatEvent {
        hits = List.copyOf(hits);
    }
}
