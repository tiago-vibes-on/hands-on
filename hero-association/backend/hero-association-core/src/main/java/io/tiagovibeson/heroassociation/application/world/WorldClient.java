package io.tiagovibeson.heroassociation.application.world;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tiagovibeson.heroassociation.contract.WorldContract.Plan;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class WorldClient {
    @Inject ObjectMapper mapper;
    @ConfigProperty(name = "hero-association.world.base-url", defaultValue = "http://localhost:17086") String baseUrl;
    @ConfigProperty(name = "hero-association.world.service-key") Optional<String> serviceKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public Plan plan(UUID mapId) {
        String key = serviceKey.orElse("");
        if (key.length() < 32) throw new ServiceUnavailableException("World service credential is not configured.");
        var request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/$", "") + "/internal/v1/world/maps/" + mapId))
                .timeout(Duration.ofSeconds(5)).header("X-Hero-Association-World-Service-Key", key).header("Accept", "application/json");
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), request, (builder, name, value) -> builder.header(name, value));
        try {
            var response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) throw new NotFoundException("Map is unavailable.");
            if (response.statusCode() != 200 || response.body().length > 131_072) throw new ServiceUnavailableException("World catalog is unavailable.");
            Plan plan = mapper.readValue(response.body(), Plan.class);
            if (!mapId.equals(plan.map().definitionId())) throw new ServiceUnavailableException("World returned a different Map.");
            return plan;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new ServiceUnavailableException("World catalog request interrupted.");
        } catch (java.io.IOException | IllegalArgumentException unavailable) {
            throw new ServiceUnavailableException("World catalog is unavailable.");
        }
    }
}
