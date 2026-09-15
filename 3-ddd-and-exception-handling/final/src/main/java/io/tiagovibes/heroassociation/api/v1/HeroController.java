package io.tiagovibes.heroassociation.api.v1;

import java.net.URI;
import java.util.List;

import io.tiagovibes.heroassociation.application.HeroApplicationService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/api/v1/heroes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class HeroController {

    @Inject
    HeroApplicationService heroApplicationService;

    @POST
    public Response create(@Valid HeroCreateRequest request, @Context UriInfo uriInfo) {
        HeroResponse hero = HeroResponse.from(
                heroApplicationService.register(request.name(), request.alias(), request.power()));

        URI location = uriInfo.getAbsolutePathBuilder().path(hero.id().toString()).build();
        return Response.created(location).entity(hero).build();
    }

    @GET
    public List<HeroResponse> findAll() {
        return heroApplicationService.findAll().stream().map(HeroResponse::from).toList();
    }

    @GET
    @Path("/{id}")
    public HeroResponse findById(@PathParam("id") Long id) {
        return HeroResponse.from(heroApplicationService.findById(id));
    }

    @PUT
    @Path("/{id}")
    public HeroResponse replace(@PathParam("id") Long id, @Valid HeroUpdateRequest request) {
        return HeroResponse.from(heroApplicationService.replace(id, request.name(), request.alias(), request.power()));
    }

    @PATCH
    @Path("/{id}")
    public HeroResponse updatePartially(@PathParam("id") Long id, @Valid HeroPatchRequest request) {
        if (request.isEmpty()) {
            throw new BadRequestException("At least one field must be provided.");
        }

        return HeroResponse.from(heroApplicationService.update(id, request.name(), request.alias(), request.power()));
    }
}
