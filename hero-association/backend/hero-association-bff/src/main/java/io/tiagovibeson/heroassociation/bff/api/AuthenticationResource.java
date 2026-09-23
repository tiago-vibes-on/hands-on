package io.tiagovibeson.heroassociation.bff.api;

import java.net.URI;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;

@Path("/auth")
public class AuthenticationResource {

    @ConfigProperty(name = "hero-association.frontend-url")
    String frontendUrl;

    @GET
    @Path("/login")
    @Authenticated
    public Response login() {
        return Response.seeOther(URI.create(frontendUrl)).build();
    }

    @GET
    @Path("/post-logout")
    public Response postLogout(@QueryParam("state") String state, @CookieParam("q_post_logout") Cookie logoutState) {
        if (state == null || logoutState == null || !state.equals(logoutState.getValue())) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        return Response.seeOther(URI.create(frontendUrl)).build();
    }
}
