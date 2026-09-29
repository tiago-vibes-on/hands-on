package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;

import io.tiagovibeson.heroassociation.application.HeroBorrowingService;
import io.tiagovibeson.heroassociation.application.HeroRecruitmentService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/agencies/{agencyId}/heroes")
@Produces(MediaType.APPLICATION_JSON)
public class HeroController {

    @Inject
    HeroRecruitmentService heroRecruitmentService;

    @Inject
    HeroBorrowingService heroBorrowingService;

    @GET
    @Path("/{heroId}")
    public AgencyStateResponse.HeroResponse findDetail(
            @PathParam("agencyId") UUID agencyId,
            @PathParam("heroId") UUID heroId) {
        return heroRecruitmentService.findDetail(agencyId, heroId);
    }

    @PUT
    @Path("/{heroId}/borrowing-fee")
    @Consumes(MediaType.APPLICATION_JSON)
    public AgencyStateResponse setBorrowingFee(
            @PathParam("agencyId") UUID agencyId,
            @PathParam("heroId") UUID heroId,
            @NotNull @Valid SetBorrowingFeeRequest request) {
        return heroBorrowingService.setFee(agencyId, heroId, request.feeGold());
    }
}
