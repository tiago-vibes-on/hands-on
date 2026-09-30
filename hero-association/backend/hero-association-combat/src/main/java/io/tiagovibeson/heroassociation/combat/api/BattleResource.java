package io.tiagovibeson.heroassociation.combat.api;

import java.util.UUID;

import io.tiagovibeson.heroassociation.combat.application.BattleAdvanceService;
import io.tiagovibeson.heroassociation.combat.application.BattleStartService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/internal/v1/battles")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BattleResource {

    private final BattleStartService battleStartService;
    private final BattleAdvanceService battleAdvanceService;

    public BattleResource(BattleStartService battleStartService, BattleAdvanceService battleAdvanceService) {
        this.battleStartService = battleStartService;
        this.battleAdvanceService = battleAdvanceService;
    }

    @POST
    @RolesAllowed("combat:start")
    public Response start(StartBattleRequest request) {
        StartBattleResponse result = battleStartService.start(request);
        return Response.status(result.replayed() ? Response.Status.OK : Response.Status.CREATED)
                .entity(result)
                .build();
    }

    @POST
    @Path("{battleId}/advance")
    @RolesAllowed("combat:advance")
    public AdvanceBattleResponse advance(@PathParam("battleId") UUID battleId, AdvanceBattleRequest request) {
        return battleAdvanceService.advance(battleId, request);
    }
}
