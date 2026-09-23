package io.tiagovibeson.heroassociation.bff.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class GameCoreClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    @ConfigProperty(name = "hero-association.core.base-url")
    String coreBaseUrl;

    public Response forward(
            String method,
            String path,
            String rawQuery,
            String accept,
            String contentType,
            String accessToken,
            byte[] requestBody) {
        HttpRequest.Builder request = HttpRequest.newBuilder(coreUri(path, rawQuery))
                .timeout(REQUEST_TIMEOUT)
                .header(HttpHeaders.ACCEPT, accept == null ? MediaType.APPLICATION_JSON : accept)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

        if (contentType != null) {
            request.header(HttpHeaders.CONTENT_TYPE, contentType);
        }

        request.method(
                method,
                requestBody == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(requestBody));

        try {
            HttpResponse<byte[]> coreResponse = httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            Response.ResponseBuilder response = Response.status(coreResponse.statusCode())
                    .entity(coreResponse.body());
            coreResponse.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                    .ifPresent(value -> response.header(HttpHeaders.CONTENT_TYPE, value));
            return response.build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unavailableResponse();
        } catch (IOException exception) {
            return unavailableResponse();
        }
    }

    private URI coreUri(String path, String rawQuery) {
        String baseUrl = coreBaseUrl.endsWith("/")
                ? coreBaseUrl.substring(0, coreBaseUrl.length() - 1)
                : coreBaseUrl;
        String pathSuffix = path == null || path.isBlank() ? "" : "/" + path;
        String querySuffix = rawQuery == null || rawQuery.isBlank() ? "" : "?" + rawQuery;
        return URI.create(baseUrl + "/api" + pathSuffix + querySuffix);
    }

    private Response unavailableResponse() {
        return Response.status(Response.Status.BAD_GATEWAY)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", "Game Core is unavailable."))
                .build();
    }
}
