package io.tiagovibeson.heroassociation.market;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.tiagovibeson.heroassociation.market.MarketContracts.*;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/market") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON) @Authenticated
public class MarketResource {
    @Inject MarketFacade market;
    @Inject MarketTransactions transactions;
    @Inject SecurityIdentity identity;
    @ConfigProperty(name = "hero-association.market.test-access-token") Optional<String> testToken;
    @GET @Path("/orders") public List<OrderView> book() { return transactions.book(); }
    @POST @Path("/orders") public Response place(@NotNull @Valid PlaceRequest request) { return market.place(subject(), token(), request); }
    @GET @Path("/orders/{id}") public OrderView order(@PathParam("id") UUID id) { return market.order(token(), id); }
    @DELETE @Path("/orders/{id}") public Response cancel(@PathParam("id") UUID id) { return market.cancel(token(), id); }
    @GET @Path("/placements/{id}") public PlacementView placement(@PathParam("id") UUID id) { return market.placement(subject(), id); }
    private String subject() {
        return identity.getPrincipal() instanceof JsonWebToken jwt ? jwt.getSubject() : identity.getPrincipal().getName();
    }
    private String token() {
        var credential = identity.getCredential(AccessTokenCredential.class);
        if (credential != null) return credential.getToken();
        if (LaunchMode.current() == LaunchMode.TEST && testToken.isPresent()) return testToken.get();
        throw new MarketException(401, "A player access token is required.");
    }
}
