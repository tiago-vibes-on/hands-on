package io.tiagovibeson.heroassociation.api.internal;

import java.util.UUID;

import io.tiagovibeson.heroassociation.application.expedition.ExpeditionBaseline;

/** Trusted Core baseline consumed server-to-server by Expedition. */
public record ExpeditionAdmissionResponse(
        UUID expeditionId,
        UUID ownerManagerId,
        UUID agencyId,
        UUID partyId,
        UUID mapId,
        int mapVersion,
        ExpeditionBaseline baseline,
        io.tiagovibeson.heroassociation.contract.WorldContract.Plan world) { }
