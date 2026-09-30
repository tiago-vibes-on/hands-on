package io.tiagovibeson.heroassociation.bff.expedition;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

/** Server-side Expedition HTTP proxy; the browser keeps its opaque BFF session. */
@ApplicationScoped
public class ExpeditionClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

    @Inject OpenTelemetry telemetry;

    @ConfigProperty(name = "hero-association.expedition.base-url")
    String baseUrl;

    @ConfigProperty(name = "hero-association.expedition.bff-service-key")
    Optional<String> bffServiceKey;

    public Response forward(String method, String path, String rawQuery, String accept,
                            String contentType, String accessToken, byte[] body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path, rawQuery))
                .timeout(REQUEST_TIMEOUT)
                .header(HttpHeaders.ACCEPT, accept == null ? MediaType.APPLICATION_JSON : accept)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        if (contentType != null) request.header(HttpHeaders.CONTENT_TYPE, contentType);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body));

        Span span = telemetry.getTracer(ExpeditionClient.class.getName())
                .spanBuilder("expedition.request")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("http.request.method", method)
                .setAttribute("server.address", "expedition")
                .startSpan();
        Scope scope = span.makeCurrent();
        try {
            telemetry.getPropagators().getTextMapPropagator()
                    .inject(Context.current(), request, (carrier, key, value) -> carrier.header(key, value));
            HttpResponse<byte[]> upstream = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            span.setAttribute("http.response.status_code", upstream.statusCode());
            if (upstream.statusCode() >= 500) span.setStatus(StatusCode.ERROR);
            Response.ResponseBuilder response = Response.status(upstream.statusCode()).entity(upstream.body());
            upstream.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                    .ifPresent(value -> response.header(HttpHeaders.CONTENT_TYPE, value));
            return response.build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            span.setStatus(StatusCode.ERROR, "Expedition request interrupted");
            return unavailable();
        } catch (IOException exception) {
            span.setStatus(StatusCode.ERROR, "Expedition unavailable");
            return unavailable();
        } finally {
            scope.close();
            span.end();
        }
    }

    public CompletableFuture<VisualResponse> visual(UUID ownerManagerId, UUID expeditionId) {
        String key = bffServiceKey.orElse(null);
        if (key == null || key.length() < 32) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Expedition visual service key is not configured."));
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        URI endpoint = URI.create(base + "/internal/v1/expedition-visuals/"
                + ownerManagerId + "/" + expeditionId);
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(REQUEST_TIMEOUT)
                .header("X-Hero-Association-Bff-Service-Key", key)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON).GET().build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> new VisualResponse(response.statusCode(),
                        new String(response.body(), StandardCharsets.UTF_8)));
    }

    public record VisualResponse(int status, String body) { }

    private URI uri(String path, String rawQuery) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String suffix = path == null || path.isBlank() ? "" : "/" + path;
        String query = rawQuery == null || rawQuery.isBlank() ? "" : "?" + rawQuery;
        return URI.create(base + "/api" + suffix + query);
    }

    private Response unavailable() {
        return Response.status(Response.Status.BAD_GATEWAY)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", "Expedition is unavailable."))
                .build();
    }
}
