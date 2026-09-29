package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.application.HeroRecruitmentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/recruits")
@Produces(MediaType.APPLICATION_JSON)
public class RecruitmentController {

    @Inject
    HeroRecruitmentService heroRecruitmentService;

    @GET
    public List<RecruitResponse> listRecruitable() {
        return heroRecruitmentService.listRecruitable().stream()
                .map(RecruitResponse::from)
                .toList();
    }

    @POST
    @Path("/{recruitId}/claim")
    public AgencyStateResponse.HeroResponse claimPersonally(@PathParam("recruitId") UUID recruitId) {
        return heroRecruitmentService.recruitPersonally(recruitId);
    }
}
