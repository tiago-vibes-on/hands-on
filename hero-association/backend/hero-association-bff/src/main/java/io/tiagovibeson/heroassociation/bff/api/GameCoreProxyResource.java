package io.tiagovibeson.heroassociation.bff.api;

import java.util.Map;
import java.util.Optional;

import io.tiagovibeson.heroassociation.bff.core.GameCoreClient;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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

    @Inject
    GameCoreClient gameCoreClient;

    @Inject
    SecurityIdentity securityIdentity;

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
}
