package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;

import io.tiagovibeson.heroassociation.application.HeroRecruitmentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/agencies/{agencyId}/recruits")
@Produces(MediaType.APPLICATION_JSON)
public class AgencyRecruitmentController {

    @Inject
    HeroRecruitmentService heroRecruitmentService;

    @POST
    @Path("/{recruitId}/claim")
    public AgencyStateResponse claimForAgency(
            @PathParam("agencyId") UUID agencyId,
            @PathParam("recruitId") UUID recruitId) {
        return heroRecruitmentService.recruitForAgency(agencyId, recruitId);
    }
}
