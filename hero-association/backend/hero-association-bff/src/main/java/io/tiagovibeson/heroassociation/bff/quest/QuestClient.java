package io.tiagovibeson.heroassociation.bff.quest;

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
public class QuestClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    @Inject
    OpenTelemetry openTelemetry;

    @ConfigProperty(name = "hero-association.quest.base-url")
    String questBaseUrl;

    public Response forward(
            String method,
            String path,
            String rawQuery,
            String accept,
            String contentType,
            String accessToken,
            byte[] requestBody) {
        HttpRequest.Builder request = HttpRequest.newBuilder(questUri(path, rawQuery))
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

        Span span = openTelemetry.getTracer(QuestClient.class.getName())
                .spanBuilder("quest.request")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("http.request.method", method)
                .setAttribute("server.address", "quest")
                .startSpan();
        Scope scope = span.makeCurrent();
        try {
            openTelemetry.getPropagators().getTextMapPropagator()
                    .inject(Context.current(), request, (carrier, key, value) -> carrier.header(key, value));
            HttpResponse<byte[]> questResponse = httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            span.setAttribute("http.response.status_code", questResponse.statusCode());
            if (questResponse.statusCode() >= 500) {
                span.setStatus(StatusCode.ERROR);
            }
            Response.ResponseBuilder response = Response.status(questResponse.statusCode())
                    .entity(questResponse.body());
            questResponse.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                    .ifPresent(value -> response.header(HttpHeaders.CONTENT_TYPE, value));
            return response.build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            span.setStatus(StatusCode.ERROR, "Quest request interrupted");
            return unavailableResponse();
        } catch (IOException exception) {
            span.setStatus(StatusCode.ERROR, "Quest unavailable");
            return unavailableResponse();
        } finally {
            scope.close();
            span.end();
        }
    }

    private URI questUri(String path, String rawQuery) {
        String baseUrl = questBaseUrl.endsWith("/")
                ? questBaseUrl.substring(0, questBaseUrl.length() - 1)
                : questBaseUrl;
        String pathSuffix = path == null || path.isBlank() ? "" : "/" + path;
        String querySuffix = rawQuery == null || rawQuery.isBlank() ? "" : "?" + rawQuery;
        return URI.create(baseUrl + "/api" + pathSuffix + querySuffix);
    }

    private Response unavailableResponse() {
        return Response.status(Response.Status.BAD_GATEWAY)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", "Quest is unavailable."))
                .build();
    }
}
