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
        CreatureProfile creature) {

    public PreparedEntry {
        Objects.requireNonNull(expeditionId);
        Objects.requireNonNull(ownerManagerId);
        Objects.requireNonNull(agencyId);
        Objects.requireNonNull(partyId);
        Objects.requireNonNull(mapId);
        Objects.requireNonNull(creature);
        heroes = List.copyOf(heroes);
        if (expeditionId.version() != 7 || ownerManagerId.version() != 7 || agencyId.version() != 7
                || partyId.version() != 7 || mapId.version() != 7 || creature.definitionId().version() != 7
                || mapVersion < 1 || heroes.isEmpty() || heroes.stream().noneMatch(hero -> hero.health() > 0)
                || heroes.stream().anyMatch(hero -> hero.heroId().version() != 7)) {
            throw new IllegalArgumentException("Prepared Expedition entry needs UUIDv7 IDs and living Heroes.");
        }
    }
}
