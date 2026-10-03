package io.tiagovibeson.heroassociation.expedition;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tiagovibeson.heroassociation.contract.QuestContract.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class QuestClient {
    @Inject ObjectMapper mapper;
    @ConfigProperty(name = "hero-association.quest.base-url", defaultValue = "http://localhost:17087") String baseUrl;
    @ConfigProperty(name = "hero-association.quest.expedition-service-key") Optional<String> serviceKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public Pin pin(PreparedEntry entry) {
        var request = new PinRequest(entry.expeditionId(), entry.ownerManagerId(), entry.agencyId(), entry.mapId(), entry.mapVersion());
        Pin pin = send("/admissions", request, Pin.class);
        if (!entry.expeditionId().equals(pin.expeditionId()) || !entry.ownerManagerId().equals(pin.ownerManagerId())
                || !entry.agencyId().equals(pin.agencyId()) || !entry.mapId().equals(pin.mapId()) || entry.mapVersion() != pin.mapVersion())
            throw new IllegalStateException("Quest admission response differs from the reserved Expedition.");
        return pin;
    }
    public void release(UUID expeditionId, UUID managerId) { send("/admissions/" + expeditionId + "/release-proven-absent", Map.of("managerId", managerId), ReleaseResponse.class); }
    public List<Orphan> orphans() { return List.of(send("/admissions/orphan-candidates", null, Orphan[].class)); }
    private <T> T send(String path, Object body, Class<T> type) {
        String key = serviceKey.orElse("");
        if (key.length() < 32) throw new IllegalStateException("Quest admission credentials are not configured.");
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/$", "") + "/internal/v1/quests" + path)).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json").header("X-Hero-Association-Quest-Expedition-Service-Key", key);
            if (body == null) request.GET(); else request.POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length > 131_072) throw new IllegalStateException("Quest admission is unavailable.");
            return mapper.readValue(response.body(), type);
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Quest admission interrupted."); }
        catch (java.io.IOException unavailable) { throw new IllegalStateException("Quest admission is unavailable."); }
    }
    public record ReleaseResponse(boolean released) { }
    public record Orphan(UUID expeditionId, UUID ownerManagerId) { }
}
