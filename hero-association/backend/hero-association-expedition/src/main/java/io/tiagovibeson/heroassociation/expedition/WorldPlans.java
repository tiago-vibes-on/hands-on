package io.tiagovibeson.heroassociation.expedition;

import java.util.List;
import java.util.UUID;
import io.tiagovibeson.heroassociation.contract.WorldContract.*;

final class WorldPlans {
    private WorldPlans() { }
    static RunState.CreatureProfile profile(Creature value) {
        return new RunState.CreatureProfile(value.definitionId(), value.version(), value.name(), value.maxHealth(), value.maxMana(),
                value.attackDamage(), value.attackIntervalMilliseconds(), value.healthRecoveryPerSecond(), value.manaRecoveryPerSecond(),
                value.criticalChance(), value.criticalDamageMultiplier(), value.baseExperience());
    }
    /** Convenience for pure engine fixtures; public admission always requires a World plan. */
    static Plan field(UUID mapId, int version, RunState.CreatureProfile creature) {
        var definition = new Creature(creature.definitionId(), creature.definitionVersion(), creature.name(), creature.baseExperience(),
                creature.maxHealth(), creature.maxMana(), creature.attackDamage(), creature.attackIntervalMilliseconds(),
                creature.healthRecoveryPerSecond(), creature.manaRecoveryPerSecond(), creature.criticalChance(), creature.criticalDamageMultiplier(), new GoldDrop(1, 1, 0), List.of());
        var encounter = new Encounter(mapId, "Encounter", false, List.of(new Spawn(definition.definitionId(), definition.version(), 3)));
        return new Plan(new MapDefinition(mapId, version, "Troll Field", MapKind.FIELD,
                List.of(new Floor(1, "Field", "OPEN_FIELD", List.of(encounter)))), List.of(definition));
    }
}
