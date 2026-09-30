package io.tiagovibeson.heroassociation.api.internal;

import java.util.UUID;

import io.tiagovibeson.heroassociation.application.expedition.ExpeditionBaseline;
import io.tiagovibeson.heroassociation.domain.CreatureCombatProfile;

/** Trusted Core baseline consumed server-to-server by Expedition. */
public record ExpeditionAdmissionResponse(
        UUID expeditionId,
        UUID ownerManagerId,
        UUID agencyId,
        UUID partyId,
        UUID mapId,
        int mapVersion,
        ExpeditionBaseline baseline,
        CreatureCombatProfile creature) { }
