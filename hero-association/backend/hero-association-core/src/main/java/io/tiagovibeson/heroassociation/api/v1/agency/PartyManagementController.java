package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;

import io.tiagovibeson.heroassociation.application.PartyManagementService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/agencies/{agencyId}/parties")
@Produces(MediaType.APPLICATION_JSON)
public class PartyManagementController {

    @Inject
    PartyManagementService partyManagementService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public AgencyStateResponse createParty(
            @PathParam("agencyId") UUID agencyId,
            @NotNull @Valid CreatePartyRequest request) {
        return partyManagementService.createParty(agencyId, request.name());
    }

    @PUT
    @Path("/{partyId}/heroes/{heroId}")
    public AgencyStateResponse addHero(
            @PathParam("agencyId") UUID agencyId,
            @PathParam("partyId") UUID partyId,
            @PathParam("heroId") UUID heroId) {
        return partyManagementService.addHero(agencyId, partyId, heroId);
    }

    @DELETE
    @Path("/{partyId}/heroes/{heroId}")
    public AgencyStateResponse removeHero(
            @PathParam("agencyId") UUID agencyId,
            @PathParam("partyId") UUID partyId,
            @PathParam("heroId") UUID heroId) {
        return partyManagementService.removeHero(agencyId, partyId, heroId);
    }
}
