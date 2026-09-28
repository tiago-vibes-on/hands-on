package io.tiagovibeson.heroassociation.bff.api;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import io.tiagovibeson.heroassociation.bff.core.GameCoreClient;
import io.tiagovibeson.heroassociation.bff.market.MarketOrderRateLimiter;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/api")
@Consumes(MediaType.WILDCARD)
@Produces(MediaType.WILDCARD)
@Authenticated
public class GameCoreProxyResource {

    private static final Logger LOG = Logger.getLogger(GameCoreProxyResource.class);
    private static final Pattern MARKET_ORDER_PLACEMENT =
            Pattern.compile("^v1/market/orders/?$");

    @Inject
    GameCoreClient gameCoreClient;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    MarketOrderRateLimiter marketOrderRateLimiter;

    @ConfigProperty(name = "hero-association.core.test-access-token")
    Optional<String> testAccessToken;

    @GET
    @Path("{path:.*}")
    public Response get(
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) {
        return forward("GET", path, uriInfo, headers, null);
    }

    @POST
    @Path("{path:.*}")
    public Response post(
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers,
            byte[] body) {
        if (MARKET_ORDER_PLACEMENT.matcher(path).matches()) {
            String subject = authenticatedSubject();
            if (subject == null || subject.isBlank()) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("message", "An authenticated user is required."))
                        .build();
            }
            try {
                if (!marketOrderRateLimiter.tryAcquire(subject)) {
                    return Response.status(429)
                            .type(MediaType.APPLICATION_JSON)
                            .header("Retry-After", "1")
                            .entity(Map.of("message", "Market order placement is limited to five requests per second."))
                            .build();
                }
            } catch (RuntimeException exception) {
                LOG.error("Market order rate limiter is unavailable.", exception);
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .type(MediaType.APPLICATION_JSON)
                        .header("Retry-After", "1")
                        .entity(Map.of("message", "Market order placement is temporarily unavailable."))
                        .build();
            }
        }
        return forward("POST", path, uriInfo, headers, body);
    }

    @PUT
    @Path("{path:.*}")
    public Response put(
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers,
            byte[] body) {
        return forward("PUT", path, uriInfo, headers, body);
    }

    @DELETE
    @Path("{path:.*}")
    public Response delete(
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) {
        return forward("DELETE", path, uriInfo, headers, null);
    }

    private Response forward(
            String method,
            String path,
            UriInfo uriInfo,
            HttpHeaders headers,
            byte[] body) {
        String accessToken = accessToken();
        if (accessToken == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("message", "An authenticated access token is required."))
                    .build();
        }

        return gameCoreClient.forward(
                method,
                path,
                uriInfo.getRequestUri().getRawQuery(),
                headers.getHeaderString(HttpHeaders.ACCEPT),
                headers.getHeaderString(HttpHeaders.CONTENT_TYPE),
                accessToken,
                body);
    }

    private String accessToken() {
        AccessTokenCredential accessTokenCredential = securityIdentity.getCredential(AccessTokenCredential.class);
        if (accessTokenCredential != null) {
            return accessTokenCredential.getToken();
        }
        return LaunchMode.current() == LaunchMode.TEST ? testAccessToken.orElse(null) : null;
    }

    private String authenticatedSubject() {
        if (securityIdentity.getPrincipal() instanceof JsonWebToken token) {
            return token.getSubject();
        }
        return LaunchMode.current() == LaunchMode.TEST
                ? securityIdentity.getPrincipal().getName()
                : null;
    }
}
