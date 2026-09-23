package io.tiagovibeson.heroassociation.domain.combat;

import java.util.List;

public record CombatBattleSnapshot(
        long currentTimeMilliseconds,
        long nextRecoveryAt,
        CombatStatus status,
        List<CombatantSnapshot> heroes,
        List<CombatantSnapshot> creatures) {

    public CombatBattleSnapshot {
        heroes = List.copyOf(heroes);
        creatures = List.copyOf(creatures);
    }
}
