package io.tiagovibeson.heroassociation.assets.application;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.tiagovibeson.heroassociation.assets.domain.AssetOwnerType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class CoreAuthorityClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @Inject ObjectMapper mapper;
    @Inject OpenTelemetry telemetry;
    @ConfigProperty(name = "hero-association.core.base-url") String baseUrl;
    @ConfigProperty(name = "hero-association.assets.core-service-key") Optional<String> key;
    public OwnerContext owner(AssetOwnerType type, UUID owner, String token) {
        var context = call("/context", new OwnerRequest(type, owner), token, OwnerContext.class);
        if (context == null || context.managerId() == null || context.ownerId() == null
                || context.ownerType() != type || context.ownerName() == null || context.ownerName().isBlank()
                || (owner != null && !owner.equals(context.ownerId()))
                || (type == AssetOwnerType.MANAGER && !context.managerId().equals(context.ownerId()))) {
            throw failure(503, "Core returned an invalid owner authorization.");
        }
        return context;
    }
    public TransferContext transfer(String direction, String agencyName, String managerName, String token) {
        var context = call("/transfers", new TransferRequest(direction, agencyName, managerName), token, TransferContext.class);
        if (context == null || context.requesterManagerId() == null || context.managerId() == null
                || context.agencyId() == null || context.managerName() == null || context.managerName().isBlank()
                || context.agencyName() == null || context.agencyName().isBlank()
                || ("MANAGER_TO_AGENCY".equals(direction) && !context.requesterManagerId().equals(context.managerId()))) {
            throw failure(503, "Core returned an invalid transfer authorization.");
        }
        return context;
    }
    private <T> T call(String path, Object body, String token, Class<T> type) {
        String serviceKey = key.filter(value -> value.length() >= 32).orElseThrow(() -> failure(503, "Core authority credentials are not configured."));
        var request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + "/internal/v1/asset-authority" + path))
                .timeout(Duration.ofSeconds(5)).header("X-Hero-Association-Assets-Core-Service-Key", serviceKey)
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json");
        var span = telemetry.getTracer(CoreAuthorityClient.class.getName()).spanBuilder("core.asset-authority").setSpanKind(SpanKind.CLIENT).startSpan();
        try (var scope = span.makeCurrent()) {
            request.POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
            telemetry.getPropagators().getTextMapPropagator().inject(Context.current(), request, (carrier, name, value) -> carrier.header(name, value));
            var result = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            span.setAttribute("http.response.status_code", result.statusCode());
            if (result.statusCode() < 200 || result.statusCode() >= 300) {
                String message = "Core authorization returned HTTP " + result.statusCode() + ".";
                try { message = mapper.readTree(result.body()).path("message").asText(message); } catch (IOException ignored) { }
                throw failure(result.statusCode() >= 500 ? 503 : result.statusCode(), message);
            }
            return mapper.readValue(result.body(), type);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw failure(503, "Core authorization was interrupted.");
        } catch (IOException unavailable) { throw failure(503, "Core authorization is temporarily unavailable."); }
        finally { span.end(); }
    }
    private WebApplicationException failure(int status, String message) {
        return new WebApplicationException(Response.status(status).entity(java.util.Map.of("message", message)).build());
    }
    public record OwnerRequest(AssetOwnerType ownerType, UUID ownerId) { }
    public record OwnerContext(UUID managerId, AssetOwnerType ownerType, UUID ownerId, String ownerName) { }
    public record TransferRequest(String direction, String agencyName, String managerName) { }
    public record TransferContext(UUID requesterManagerId, UUID managerId, String managerName, UUID agencyId, String agencyName) { }
}
