package io.tiagovibeson.heroassociation.expedition;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.expedition.RunState.CreatureProfile;
import io.tiagovibeson.heroassociation.expedition.RunState.HeroState;
import jakarta.enterprise.context.ApplicationScoped;

/** Server-side admission boundary. The browser never supplies a Hero baseline. */
@ApplicationScoped
public class CoreAdmissionClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper mapper;

    @ConfigProperty(name = "hero-association.core.base-url", defaultValue = "http://localhost:17081")
    String coreBaseUrl;

    @ConfigProperty(name = "hero-association.expedition.core-service-key")
    Optional<String> serviceKey;

    public CoreAdmissionClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public PreparedEntry reserve(UUID expeditionId, UUID agencyId, UUID partyId,
                                 UUID mapId, String playerAccessToken) {
        if (playerAccessToken == null || playerAccessToken.isBlank()) {
            throw new IllegalArgumentException("A player access token is required for Core admission.");
        }
        byte[] body = json(new ReserveRequest(expeditionId, agencyId, partyId, mapId));
        HttpRequest request = request("/reservations")
                .header("Authorization", "Bearer " + playerAccessToken)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        AdmissionResponse response = decode(send(request), AdmissionResponse.class);
        if (!expeditionId.equals(response.expeditionId()) || !agencyId.equals(response.agencyId())
                || !partyId.equals(response.partyId()) || !mapId.equals(response.mapId())) {
            throw new IllegalStateException("Core admission response disagrees with the requested run.");
        }
        List<HeroState> heroes = response.baseline().heroes().stream()
                .map(hero -> new HeroState(hero.heroId(), hero.name(), hero.heroClass(),
                        hero.experience(), hero.skillPoints(), hero.health(), hero.mana(),
                        hero.staminaMilliseconds(), hero.runeIds(), hero.criticalChance(),
                        hero.criticalDamageMultiplier()))
                .toList();
        if (response.world() == null) throw new IllegalStateException("Core admission omitted the pinned Map plan.");
        CreatureProfile profile = WorldPlans.profile(response.world().creature(response.world().map().encounter(1).spawns().getFirst()));
        return new PreparedEntry(response.expeditionId(), response.ownerManagerId(),
                response.agencyId(), response.partyId(), response.mapId(),
                response.mapVersion(), heroes, profile, response.world());
    }

    public UUID currentManagerId(String playerAccessToken) {
        if (playerAccessToken == null || playerAccessToken.isBlank()) {
            throw new IllegalArgumentException("A player access token is required.");
        }
        ManagerIdentityResponse identity = decode(send(request("/me")
                .header("Authorization", "Bearer " + playerAccessToken)
                .GET().build()), ManagerIdentityResponse.class);
        if (identity.managerId() == null || identity.managerId().version() != 7) {
            throw new IllegalStateException("Core returned an invalid Manager identity.");
        }
        return identity.managerId();
    }

    public List<ReservationCandidate> orphanCandidates() {
        ReservationCandidate[] candidates = decode(send(request("/reservations/orphan-candidates")
                .GET().build()), ReservationCandidate[].class);
        return List.of(candidates);
    }

    public boolean releaseProvenAbsent(UUID expeditionId, UUID managerId) {
        byte[] body = json(new ReleaseRequest(managerId));
        ReleaseResponse response = decode(send(request("/reservations/" + expeditionId
                + "/release-proven-absent")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build()), ReleaseResponse.class);
        return response.released();
    }

    private HttpRequest.Builder request(String path) {
        String key = serviceKey.orElseThrow(() -> new IllegalStateException("Core admission key is not configured."));
        if (key.length() < 32) {
            throw new IllegalStateException("Core admission key must be at least 32 characters.");
        }
        String base = coreBaseUrl.endsWith("/") ? coreBaseUrl.substring(0, coreBaseUrl.length() - 1) : coreBaseUrl;
        return HttpRequest.newBuilder(URI.create(base + "/internal/v1/expedition-admissions" + path))
                .timeout(TIMEOUT)
                .header("X-Hero-Association-Service-Key", key)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }

    private byte[] send(HttpRequest request) {
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CoreAdmissionException(response.statusCode());
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Core admission request interrupted.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Core admission is unavailable.", exception);
        }
    }

    private byte[] json(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode Core admission request.", exception);
        }
    }

    private <T> T decode(byte[] body, Class<T> type) {
        try {
            return mapper.readValue(body, type);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not decode trusted Core admission response.", exception);
        }
    }

    public static final class CoreAdmissionException extends RuntimeException {
        private final int status;

        public CoreAdmissionException(int status) {
            super("Core admission returned HTTP " + status);
            this.status = status;
        }

        public int status() { return status; }
    }

    private record ReserveRequest(UUID expeditionId, UUID agencyId, UUID partyId, UUID mapId) { }
    private record ReleaseRequest(UUID managerId) { }
    private record ReleaseResponse(boolean released) { }
    private record ManagerIdentityResponse(UUID managerId) { }
    public record ReservationCandidate(UUID expeditionId, UUID ownerManagerId) { }
    private record AdmissionResponse(UUID expeditionId, UUID ownerManagerId, UUID agencyId, UUID partyId,
                                     UUID mapId, int mapVersion, Baseline baseline,
                                     io.tiagovibeson.heroassociation.contract.WorldContract.Plan world) { }
    private record Baseline(List<HeroBaseline> heroes) { }
    private record HeroBaseline(UUID heroId, String name, HeroClass heroClass, long experience,
                                Map<HeroSkill, BigDecimal> skillPoints, int health, int mana,
                                long staminaMilliseconds, String previousActivity,
                                Map<Integer, UUID> runeIds, double criticalChance,
                                double criticalDamageMultiplier) { }
}
