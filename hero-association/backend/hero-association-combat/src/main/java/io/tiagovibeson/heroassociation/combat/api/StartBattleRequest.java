package io.tiagovibeson.heroassociation.combat.api;

import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.combat.CombatBattleSnapshot;

public record StartBattleRequest(
        UUID battleId,
        BattleSource source,
        UUID runId,
        UUID partyId,
        UUID ownerManagerId,
        String rulesetVersion,
        CombatBattleSnapshot initialSnapshot,
        List<HeroBattleInput> heroInputs,
        List<CreatureBattleInput> creatureInputs) {
}
