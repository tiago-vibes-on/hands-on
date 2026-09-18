package io.tiagovibes.heroassociation;

import java.net.URI;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
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

@Path("/heroes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class HeroResource {

    @Inject
    HeroRepository heroRepository;

    @POST
    @Transactional
    public Response create(@Valid HeroRequest request, @Context UriInfo uriInfo) {
        ensureAliasIsAvailable(request.alias(), null);

        Hero hero = new Hero();
        update(hero, request);
        heroRepository.persist(hero);

        URI location = uriInfo.getAbsolutePathBuilder().path(hero.id.toString()).build();
        return Response.created(location).entity(HeroResponse.from(hero)).build();
    }

    @GET
    public List<HeroResponse> findAll() {
        return heroRepository.listAll().stream().map(HeroResponse::from).toList();
    }

    @GET
    @Path("/{id}")
    public HeroResponse findById(@PathParam("id") Long id) {
        return HeroResponse.from(findHero(id));
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public HeroResponse replace(@PathParam("id") Long id, @Valid HeroRequest request) {
        Hero hero = findHero(id);
        ensureAliasIsAvailable(request.alias(), hero.id);
        update(hero, request);
        return HeroResponse.from(hero);
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public HeroResponse updatePartially(@PathParam("id") Long id, @Valid HeroPatchRequest request) {
        if (request.isEmpty()) {
            throw new BadRequestException(errorResponse(Response.Status.BAD_REQUEST, "At least one field must be provided."));
        }

        Hero hero = findHero(id);
        if (request.alias() != null) {
            ensureAliasIsAvailable(request.alias(), hero.id);
            hero.alias = request.alias();
        }
        if (request.name() != null) {
            hero.name = request.name();
        }
        if (request.power() != null) {
            hero.power = request.power();
        }
        return HeroResponse.from(hero);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        heroRepository.delete(findHero(id));
        return Response.noContent().build();
    }

    private Hero findHero(Long id) {
        return heroRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException(
                        errorResponse(Response.Status.NOT_FOUND, "Hero with id " + id + " was not found.")));
    }

    private void ensureAliasIsAvailable(String alias, Long currentHeroId) {
        boolean exists = heroRepository.findByAlias(alias)
                .filter(hero -> !hero.id.equals(currentHeroId))
                .isPresent();

        if (exists) {
            throw new jakarta.ws.rs.ClientErrorException(
                    errorResponse(Response.Status.CONFLICT, "Alias '" + alias + "' is already registered."));
        }
    }

    private Response errorResponse(Response.Status status, String message) {
        return Response.status(status)
                .entity(new ApiError(message))
                .build();
    }

    private void update(Hero hero, HeroRequest request) {
        hero.name = request.name();
        hero.alias = request.alias();
        hero.power = request.power();
    }
}
