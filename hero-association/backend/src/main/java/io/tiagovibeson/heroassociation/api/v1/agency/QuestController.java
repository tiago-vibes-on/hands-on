package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;

import io.tiagovibeson.heroassociation.application.QuestStartService;
import io.tiagovibeson.heroassociation.application.QuestCombatSyncService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/agencies/{agencyId}/quests")
@Produces(MediaType.APPLICATION_JSON)
public class QuestController {

    @Inject
    QuestStartService questStartService;

    @Inject
    QuestCombatSyncService questCombatSyncService;

    @PUT
    @Path("/{questId}/start")
    @Consumes(MediaType.APPLICATION_JSON)
    public AgencyStateResponse startQuest(
            @PathParam("agencyId") UUID agencyId,
            @PathParam("questId") UUID questId,
            @NotNull @Valid StartQuestRequest request) {
        return questStartService.startQuest(agencyId, questId, request.partyId());
    }

    @POST
    @Path("/{questId}/combat/sync")
    public AgencyStateResponse synchronizeCombat(
            @PathParam("agencyId") UUID agencyId,
            @PathParam("questId") UUID questId) {
        return questCombatSyncService.synchronize(agencyId, questId);
    }
}
