package io.tiagovibeson.heroassociation.application.quest;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tiagovibeson.heroassociation.contract.QuestContract.*;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ServiceUnavailableException;

@ApplicationScoped
public class QuestClient {
    @Inject ObjectMapper mapper;
    @ConfigProperty(name = "hero-association.quest.base-url") String baseUrl;
    @ConfigProperty(name = "hero-association.quest.core-service-key") Optional<String> serviceKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public void apply(ReturnRequest input) {
        String key = serviceKey.orElse("");
        if (key.length() < 32) throw new ServiceUnavailableException("Quest return credentials are not configured.");
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/$", "") + "/internal/v1/quests/returns"))
                    .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                    .header("X-Hero-Association-Quest-Core-Service-Key", key);
            GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), request, (builder, name, value) -> builder.header(name, value));
            var response = http.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(input))).build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length > 4096) throw new ServiceUnavailableException("Quest return awaits confirmation.");
            ReturnReceipt receipt = mapper.readValue(response.body(), ReturnReceipt.class);
            if (!input.expeditionId().equals(receipt.expeditionId()) || !input.ownerManagerId().equals(receipt.ownerManagerId())
                    || !"APPLIED".equals(receipt.status()) || !java.util.Objects.equals(receipt.assignmentId(), input.progress() == null ? null : input.progress().pin().assignment().assignmentId()))
                throw new ServiceUnavailableException("Quest return awaits a matching receipt.");
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new ServiceUnavailableException("Quest return request interrupted."); }
        catch (java.io.IOException unavailable) { throw new ServiceUnavailableException("Quest return awaits recovery."); }
    }
}
