package io.tiagovibeson.heroassociation.expedition.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.tiagovibeson.heroassociation.expedition.ExpeditionService;
import io.tiagovibeson.heroassociation.expedition.ExpeditionService.RunNotFoundException;
import io.tiagovibeson.heroassociation.expedition.ExpeditionView;
import io.tiagovibeson.heroassociation.expedition.RedisFightTimelineStore;
import io.tiagovibeson.heroassociation.expedition.RunState;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;

/** Private Redis-only visual read for BFF sockets, never routed from the browser. */
@Path("/internal/v1/expedition-visuals")
@Produces(MediaType.APPLICATION_JSON)
public class ExpeditionVisualResource {

    @Inject ExpeditionService expeditions;
    @Inject RedisFightTimelineStore timelines;

    @ConfigProperty(name = "expedition.visual-api.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "hero-association.expedition.bff-service-key")
    Optional<String> serviceKey;

    @GET
    @Path("/{ownerManagerId}/{expeditionId}")
    public ExpeditionView current(@HeaderParam("X-Hero-Association-Bff-Service-Key") String provided,
                                  @PathParam("ownerManagerId") UUID ownerManagerId,
                                  @PathParam("expeditionId") UUID expeditionId) {
        if (!enabled) throw new NotFoundException();
        requireServiceKey(provided);
        if (ownerManagerId == null || ownerManagerId.version() != 7
                || expeditionId == null || expeditionId.version() != 7) {
            throw new NotFoundException();
        }
        try {
            RunState run = expeditions.get(ownerManagerId, expeditionId);
            return ExpeditionView.from(run, Instant.now(), timelines.get(run));
        } catch (RunNotFoundException absent) {
            throw new NotFoundException();
        }
    }

    private void requireServiceKey(String provided) {
        String expected = serviceKey.orElse(null);
        if (expected == null || expected.length() < 32) {
            throw new ServiceUnavailableException("Expedition visual API is not configured.");
        }
        if (provided == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException();
        }
    }
}
