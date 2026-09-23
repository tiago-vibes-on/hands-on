package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;

import io.tiagovibeson.heroassociation.application.FeedPostService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/agencies/{agencyId}/feed-posts")
@Produces(MediaType.APPLICATION_JSON)
public class FeedController {

    @Inject
    FeedPostService feedPostService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public AgencyStateResponse createPost(
            @PathParam("agencyId") UUID agencyId,
            @NotNull @Valid CreateFeedPostRequest request) {
        return feedPostService.createPost(
                agencyId,
                request.authorType(),
                request.authorId(),
                request.content(),
                request.itemId(),
                request.itemQuantity());
    }
}
