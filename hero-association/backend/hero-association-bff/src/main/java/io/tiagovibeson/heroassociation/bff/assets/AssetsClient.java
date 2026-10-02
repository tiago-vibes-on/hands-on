package io.tiagovibeson.heroassociation.bff.assets;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class AssetsClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    @Inject
    OpenTelemetry openTelemetry;

    @ConfigProperty(name = "hero-association.assets.base-url")
    String assetsBaseUrl;

    public Response forward(
            String method,
            String path,
            String rawQuery,
            String accept,
            String contentType,
            String accessToken,
            byte[] requestBody) {
        HttpRequest.Builder request = HttpRequest.newBuilder(assetsUri(path, rawQuery))
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

        Span span = openTelemetry.getTracer(AssetsClient.class.getName())
                .spanBuilder("assets.request")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("http.request.method", method)
                .setAttribute("server.address", "assets")
                .startSpan();
        Scope scope = span.makeCurrent();
        try {
            openTelemetry.getPropagators().getTextMapPropagator()
                    .inject(Context.current(), request, (carrier, key, value) -> carrier.header(key, value));
            HttpResponse<byte[]> assetsResponse = httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            span.setAttribute("http.response.status_code", assetsResponse.statusCode());
            if (assetsResponse.statusCode() >= 500) {
                span.setStatus(StatusCode.ERROR);
            }
            Response.ResponseBuilder response = Response.status(assetsResponse.statusCode())
                    .entity(assetsResponse.body());
            assetsResponse.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                    .ifPresent(value -> response.header(HttpHeaders.CONTENT_TYPE, value));
            return response.build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            span.setStatus(StatusCode.ERROR, "Assets request interrupted");
            return unavailableResponse();
        } catch (IOException exception) {
            span.setStatus(StatusCode.ERROR, "Assets unavailable");
            return unavailableResponse();
        } finally {
            scope.close();
            span.end();
        }
    }

    private URI assetsUri(String path, String rawQuery) {
        String baseUrl = assetsBaseUrl.endsWith("/")
                ? assetsBaseUrl.substring(0, assetsBaseUrl.length() - 1)
                : assetsBaseUrl;
        String pathSuffix = path == null || path.isBlank() ? "" : "/" + path;
        String querySuffix = rawQuery == null || rawQuery.isBlank() ? "" : "?" + rawQuery;
        return URI.create(baseUrl + "/api" + pathSuffix + querySuffix);
    }

    private Response unavailableResponse() {
        return Response.status(Response.Status.BAD_GATEWAY)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", "Assets is unavailable."))
                .build();
    }
}
