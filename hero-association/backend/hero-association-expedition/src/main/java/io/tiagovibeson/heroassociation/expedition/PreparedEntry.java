package io.tiagovibeson.heroassociation.expedition;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.tiagovibeson.heroassociation.expedition.RunState.CreatureProfile;
import io.tiagovibeson.heroassociation.expedition.RunState.HeroState;

/** Trusted, already-reserved Core baseline; no browser endpoint accepts this payload. */
public record PreparedEntry(
        UUID expeditionId,
        UUID ownerManagerId,
        UUID agencyId,
        UUID partyId,
        UUID mapId,
        int mapVersion,
        List<HeroState> heroes,
        CreatureProfile creature,
        io.tiagovibeson.heroassociation.contract.WorldContract.Plan world,
        io.tiagovibeson.heroassociation.contract.QuestContract.Pin quest) {

    public PreparedEntry(UUID expeditionId, UUID ownerManagerId, UUID agencyId, UUID partyId, UUID mapId,
                         int mapVersion, List<HeroState> heroes, CreatureProfile creature,
                         io.tiagovibeson.heroassociation.contract.WorldContract.Plan world) {
        this(expeditionId, ownerManagerId, agencyId, partyId, mapId, mapVersion, heroes, creature, world, null);
    }
    public PreparedEntry withQuest(io.tiagovibeson.heroassociation.contract.QuestContract.Pin pin) {
        return new PreparedEntry(expeditionId, ownerManagerId, agencyId, partyId, mapId, mapVersion, heroes, creature, world, pin);
    }

    public PreparedEntry(UUID expeditionId, UUID ownerManagerId, UUID agencyId, UUID partyId, UUID mapId,
                         int mapVersion, List<HeroState> heroes, CreatureProfile creature) {
        this(expeditionId, ownerManagerId, agencyId, partyId, mapId, mapVersion, heroes, creature, WorldPlans.field(mapId, mapVersion, creature));
    }

    public PreparedEntry {
        Objects.requireNonNull(expeditionId);
        Objects.requireNonNull(ownerManagerId);
        Objects.requireNonNull(agencyId);
        Objects.requireNonNull(partyId);
        Objects.requireNonNull(mapId);
        Objects.requireNonNull(creature);
        Objects.requireNonNull(world);
        if (quest != null && (!expeditionId.equals(quest.expeditionId()) || !ownerManagerId.equals(quest.ownerManagerId())
                || !agencyId.equals(quest.agencyId()) || !mapId.equals(quest.mapId()) || mapVersion != quest.mapVersion()))
            throw new IllegalArgumentException("Quest pin differs from reserved Expedition.");
        heroes = List.copyOf(heroes);
        if (expeditionId.version() != 7 || ownerManagerId.version() != 7 || agencyId.version() != 7
                || partyId.version() != 7 || mapId.version() != 7 || creature.definitionId().version() != 7
                || mapVersion < 1 || !mapId.equals(world.map().definitionId()) || mapVersion != world.map().version()
                || heroes.isEmpty() || heroes.stream().noneMatch(hero -> hero.health() > 0)
                || heroes.stream().anyMatch(hero -> hero.heroId().version() != 7)) {
            throw new IllegalArgumentException("Prepared Expedition entry needs UUIDv7 IDs and living Heroes.");
        }
    }
}
