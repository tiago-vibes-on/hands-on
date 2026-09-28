package io.tiagovibeson.heroassociation.bff.api;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

@Path("/internal/market-order-identity")
public class MarketOrderIdentityResource {

    public static final String SUBJECT_HEADER = "X-Hero-Association-Subject";

    @ConfigProperty(name = "hero-association.edge-auth.enabled", defaultValue = "false")
    boolean enabled;

    @Inject
    SecurityIdentity identity;

    @POST
    public Response identify() {
        if (!enabled) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (identity.isAnonymous()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        String subject = null;
        if (identity.getPrincipal() instanceof JsonWebToken token) {
            subject = token.getSubject();
        } else if (LaunchMode.current() == LaunchMode.TEST) {
            subject = identity.getPrincipal().getName();
        }
        if (subject == null || subject.isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        return Response.ok()
                .header(SUBJECT_HEADER, subject)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
