package io.tiagovibeson.heroassociation.quest;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class QuestRemoteTestResource implements QuarkusTestResourceLifecycleManager {
    static final UUID MANAGER = UUID.fromString("019c4c00-0000-7000-8000-000000000001");
    static final Map<UUID, JsonNode> receipts = new ConcurrentHashMap<>();
    static final AtomicInteger credits = new AtomicInteger();
    static final AtomicBoolean loseResponse = new AtomicBoolean();
    static final AtomicBoolean outage = new AtomicBoolean();
    static final AtomicBoolean wrongReceipt = new AtomicBoolean();
    static final AtomicReference<UUID> actor = new AtomicReference<>(MANAGER);
    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/v1/quest-authority/me", exchange -> {
                boolean allowed = "Bearer test-quest-player-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))
                        && "test-quest-core-service-key-012345678901234567".equals(exchange.getRequestHeaders().getFirst("X-Hero-Association-Quest-Core-Service-Key"));
                byte[] body = mapper.writeValueAsBytes(Map.of("managerId", actor.get(), "atAgency", true));
                exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(allowed ? 200 : 403, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.createContext("/internal/v1/assets/quest/rewards", exchange -> {
                if (outage.get()) { exchange.sendResponseHeaders(503, -1); exchange.close(); return; }
                if (!"test-assets-quest-service-key-0123456789012345".equals(exchange.getRequestHeaders().getFirst("X-Hero-Association-Assets-Quest-Service-Key"))) {
                    exchange.sendResponseHeaders(403, -1); exchange.close(); return;
                }
                JsonNode command = mapper.readTree(exchange.getRequestBody());
                UUID id = UUID.fromString(command.path("operationKey").asText());
                JsonNode existing = receipts.putIfAbsent(id, command);
                if (existing == null) credits.incrementAndGet();
                else if (!existing.equals(command)) { exchange.sendResponseHeaders(409, -1); exchange.close(); return; }
                if (loseResponse.getAndSet(false)) { exchange.close(); return; }
                var result = mapper.createObjectNode().put("operationKey", id.toString()).put("kind", wrongReceipt.get() ? "OTHER" : "QUEST_REWARD").put("status", "APPLIED");
                result.set("request", command); result.set("heroes", mapper.createObjectNode());
                byte[] body = mapper.writeValueAsBytes(result); exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            return Map.of("hero-association.core.base-url", url, "hero-association.assets.base-url", url);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    public void stop() { if (server != null) server.stop(0); }
    static void reset() { receipts.clear(); credits.set(0); loseResponse.set(false); outage.set(false); wrongReceipt.set(false); actor.set(MANAGER); }
}
