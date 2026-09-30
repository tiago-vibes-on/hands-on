package io.tiagovibeson.heroassociation.bff.expedition;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.websockets.next.HttpUpgradeCheck;
import io.quarkus.websockets.next.UserData.TypedKey;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

/** Rejects cross-site or non-owner upgrades before a socket can receive run state. */
@ApplicationScoped
public class ExpeditionSocketUpgradeCheck implements HttpUpgradeCheck {

    static final String ENDPOINT_ID = "expedition-snapshot";
    static final TypedKey<String> OPENING_SNAPSHOT = TypedKey.forString("opening-snapshot");
    static final TypedKey<String> OWNER_MANAGER_ID = TypedKey.forString("owner-manager-id");
    static final TypedKey<String> LAST_SNAPSHOT = TypedKey.forString("last-snapshot");

    @Inject ExpeditionClient expeditions;
    @Inject ObjectMapper mapper;

    @ConfigProperty(name = "hero-association.frontend-url")
    String frontendUrl;

    @ConfigProperty(name = "hero-association.expedition.websocket.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "hero-association.core.test-access-token")
    Optional<String> testAccessToken;

    @Override
    public boolean appliesTo(String endpointId) {
        return ENDPOINT_ID.equals(endpointId);
    }

    @Override
    public Uni<CheckResult> perform(HttpUpgradeContext context) {
        if (!enabled) return CheckResult.rejectUpgrade(404);
        if (!expectedOrigin(frontendUrl).equals(context.httpRequest().getHeader("Origin"))) {
            return CheckResult.rejectUpgrade(403);
        }
        UUID expeditionId;
        try {
            expeditionId = UUID.fromString(context.pathParam("expeditionId"));
        } catch (RuntimeException invalid) {
            return CheckResult.rejectUpgrade(400);
        }
        if (expeditionId.version() != 7) return CheckResult.rejectUpgrade(400);

        UUID runId = expeditionId;
        return context.securityIdentity().onItem().transformToUni(identity -> {
            String token = accessToken(identity);
            if (token == null) return CheckResult.rejectUpgrade(401);
            return Uni.createFrom().item(() -> ownerSnapshot(context, runId, token))
                    .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
        });
    }

    private CheckResult ownerSnapshot(HttpUpgradeContext context, UUID expeditionId, String token) {
        try (Response response = expeditions.forward("GET", "v1/expeditions/" + expeditionId,
                null, "application/json", null, token, null)) {
            if (response.getStatus() == 200 && response.getEntity() instanceof byte[] bytes
                    && bytes.length > 0) {
                JsonNode snapshot = mapper.readTree(bytes);
                UUID owner = UUID.fromString(snapshot.path("ownerManagerId").asText());
                UUID returnedRun = UUID.fromString(snapshot.path("expeditionId").asText());
                if (owner.version() != 7 || !returnedRun.equals(expeditionId)) {
                    return CheckResult.rejectUpgradeSync(503);
                }
                context.userData().put(OWNER_MANAGER_ID, owner.toString());
                context.userData().put(OPENING_SNAPSHOT, new String(bytes, StandardCharsets.UTF_8));
                return CheckResult.permitUpgradeSync();
            }
            if (response.getStatus() == 401) return CheckResult.rejectUpgradeSync(401);
            if (response.getStatus() == 404 || response.getStatus() == 403) {
                return CheckResult.rejectUpgradeSync(404);
            }
            return CheckResult.rejectUpgradeSync(503);
        } catch (IOException | RuntimeException unavailable) {
            return CheckResult.rejectUpgradeSync(503);
        }
    }

    private String accessToken(SecurityIdentity identity) {
        if (identity == null || identity.isAnonymous()) return null;
        AccessTokenCredential credential = identity.getCredential(AccessTokenCredential.class);
        if (credential != null) return credential.getToken();
        return LaunchMode.current() == LaunchMode.TEST ? testAccessToken.orElse(null) : null;
    }

    static String expectedOrigin(String frontendUrl) {
        URI uri = URI.create(frontendUrl);
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Frontend URL needs an absolute origin.");
        }
        return uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
    }
}
