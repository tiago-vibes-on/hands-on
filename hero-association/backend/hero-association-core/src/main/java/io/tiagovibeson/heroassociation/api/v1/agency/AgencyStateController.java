package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;

import io.tiagovibeson.heroassociation.application.AgencyStateService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/agencies")
@Produces(MediaType.APPLICATION_JSON)
public class AgencyStateController {

    @Inject
    AgencyStateService agencyStateService;

    @GET
    @Path("/{agencyId}/state")
    public AgencyStateResponse findState(@PathParam("agencyId") UUID agencyId) {
        return agencyStateService.findState(agencyId);
    }
}
