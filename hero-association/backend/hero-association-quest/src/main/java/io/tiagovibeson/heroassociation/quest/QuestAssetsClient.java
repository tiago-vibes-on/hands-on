package io.tiagovibeson.heroassociation.quest;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import io.tiagovibeson.heroassociation.contract.QuestContract.Assignment;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ServiceUnavailableException;

@ApplicationScoped
public class QuestAssetsClient {
    @Inject ObjectMapper mapper;
    @ConfigProperty(name = "hero-association.assets.base-url") String baseUrl;
    @ConfigProperty(name = "hero-association.assets.quest-service-key") Optional<String> serviceKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public void reward(Assignment assignment) {
        String key = serviceKey.orElse("");
        if (key.length() < 32) throw new ServiceUnavailableException("Quest reward credentials are not configured.");
        var reward = assignment.definition().reward();
        var command = mapper.createObjectNode().put("operationKey", assignment.assignmentId().toString()).put("kind", "QUEST_REWARD")
                .put("managerId", assignment.ownerManagerId().toString()).put("gold", reward.gold());
        command.set("items", mapper.valueToTree(reward.items())); command.set("runes", mapper.valueToTree(reward.runes()));
        for (String field : List.of("agencyId", "heroId", "slotIndex", "runeId", "sourceOwnerType", "feeGold", "heroIds")) command.putNull(field);
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/$", "") + "/internal/v1/assets/quest/rewards"))
                    .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                    .header("X-Hero-Association-Assets-Quest-Service-Key", key)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(command))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 500) throw new ServiceUnavailableException("Quest reward awaits Assets recovery.");
            if (response.statusCode() != 200 || response.body().length > 131_072) throw new ProtocolConflict("Assets refused the pinned Quest reward.");
            JsonNode receipt = mapper.readTree(response.body());
            JsonNode normalizedCommand = mapper.readTree(mapper.writeValueAsBytes(command));
            if (!"APPLIED".equals(receipt.path("status").asText()) || !"QUEST_REWARD".equals(receipt.path("kind").asText())
                    || !assignment.assignmentId().toString().equals(receipt.path("operationKey").asText())
                    || !normalizedCommand.equals(mapper.readTree(mapper.writeValueAsBytes(receipt.path("request")))))
                throw new ProtocolConflict("Assets receipt differs from the pinned Quest reward.");
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new ServiceUnavailableException("Quest reward request interrupted."); }
        catch (java.io.IOException unavailable) { throw new ServiceUnavailableException("Quest reward awaits Assets recovery."); }
    }
    public static class ProtocolConflict extends RuntimeException {
        public ProtocolConflict(String message) { super(message); }
    }
}
