package io.tiagovibeson.heroassociation.quest;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ServiceUnavailableException;

@ApplicationScoped
public class QuestAuthorityClient {
    @Inject ObjectMapper mapper;
    @ConfigProperty(name = "hero-association.core.base-url") String baseUrl;
    @ConfigProperty(name = "hero-association.quest.core-service-key") Optional<String> serviceKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public Identity current(String playerToken) {
        String key = serviceKey.orElse("");
        if (key.length() < 32 || playerToken == null || playerToken.isBlank()) throw new ServiceUnavailableException("Quest identity authority is unavailable.");
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/$", "") + "/internal/v1/quest-authority/me"))
                    .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + playerToken)
                    .header("X-Hero-Association-Quest-Core-Service-Key", key).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length > 4096) throw new ServiceUnavailableException("Quest identity authority is unavailable.");
            Identity identity = mapper.readValue(response.body(), Identity.class);
            io.tiagovibeson.heroassociation.contract.WorldContract.id(identity.managerId()); return identity;
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new ServiceUnavailableException("Quest identity request interrupted."); }
        catch (java.io.IOException | IllegalArgumentException unavailable) { throw new ServiceUnavailableException("Quest identity authority is unavailable."); }
    }
    public record Identity(UUID managerId, boolean atAgency) { }
}
