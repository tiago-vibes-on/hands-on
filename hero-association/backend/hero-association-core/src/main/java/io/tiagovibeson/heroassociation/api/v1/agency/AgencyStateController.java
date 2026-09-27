package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;

import io.tiagovibeson.heroassociation.application.AgencyCreationService;
import io.tiagovibeson.heroassociation.application.AgencyStateService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/agencies")
@Produces(MediaType.APPLICATION_JSON)
public class AgencyStateController {

    @Inject
    AgencyStateService agencyStateService;

    @Inject
    AgencyCreationService agencyCreationService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreateAgencyRequest request) {
        AgencyStateResponse state = agencyCreationService.create(request == null ? null : request.name());
        return Response.status(Response.Status.CREATED).entity(state).build();
    }

    @GET
    @Path("/{agencyId}/state")
    public AgencyStateResponse findState(@PathParam("agencyId") UUID agencyId) {
        return agencyStateService.findState(agencyId);
    }
}
