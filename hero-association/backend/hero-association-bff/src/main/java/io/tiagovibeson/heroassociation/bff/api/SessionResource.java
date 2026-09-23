package io.tiagovibeson.heroassociation.bff.api;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/session")
@Produces(MediaType.APPLICATION_JSON)
public class SessionResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    RoutingContext routingContext;

    @GET
    public Response session() {
        if (identity.isAnonymous()) {
            return noStore(SessionResponse.anonymous(csrfToken()));
        }

        if (identity.getPrincipal() instanceof JsonWebToken token) {
            return noStore(SessionResponse.authenticated(new IdentityResponse(
                    token.getSubject(),
                    token.getClaim("preferred_username"),
                    token.getClaim("email"),
                    Boolean.TRUE.equals(token.getClaim("email_verified"))), csrfToken()));
        }

        return noStore(SessionResponse.authenticated(new IdentityResponse(
                identity.getPrincipal().getName(),
                identity.getPrincipal().getName(),
                null,
                false), csrfToken()));
    }

    private String csrfToken() {
        return routingContext.get("csrf_token");
    }

    private Response noStore(SessionResponse session) {
        return Response.ok(session)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    public record SessionResponse(boolean authenticated, IdentityResponse identity, String csrfToken) {

        static SessionResponse anonymous(String csrfToken) {
            return new SessionResponse(false, null, csrfToken);
        }

        static SessionResponse authenticated(IdentityResponse identity, String csrfToken) {
            return new SessionResponse(true, identity, csrfToken);
        }
    }

    public record IdentityResponse(
            String subject,
            String username,
            String email,
            boolean emailVerified) {
    }
}
