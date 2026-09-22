package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;

import io.tiagovibeson.heroassociation.application.HeroActivityService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/agencies/{agencyId}/heroes/{heroId}/activity")
@Produces(MediaType.APPLICATION_JSON)
public class HeroActivityController {

    @Inject
    HeroActivityService heroActivityService;

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public AgencyStateResponse changeActivity(
            @PathParam("agencyId") UUID agencyId,
            @PathParam("heroId") UUID heroId,
            @Valid ChangeHeroActivityRequest request) {
        return heroActivityService.changeActivity(agencyId, heroId, request.activity());
    }
}
