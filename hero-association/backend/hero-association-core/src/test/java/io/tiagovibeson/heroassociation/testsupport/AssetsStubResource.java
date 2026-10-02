package io.tiagovibeson.heroassociation.testsupport;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sun.net.httpserver.*;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;

/** External contract fixture; real conservation and concurrent mutation tests run in Assets. */
public class AssetsStubResource implements QuarkusTestResourceLifecycleManager {
    public static final String KEY = "test-only-assets-core-service-key-0123456789";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static ObjectNode fixture;
    private static final Map<String, JsonNode> receipts = new HashMap<>();
    public static volatile boolean unavailable;
    public static volatile boolean loseNextResponse;
    public static volatile boolean corruptNextReceipt;
    public static volatile int appliedCommands;
    private HttpServer server;

    public static synchronized void reset() {
        try (var input = AssetsStubResource.class.getResourceAsStream("/assets-contract-fixture.json")) {
            fixture = (ObjectNode) JSON.readTree(input);
            receipts.clear(); appliedCommands = 0; unavailable = false; loseNextResponse = false; corruptNextReceipt = false;
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    public static synchronized long gold(UUID owner) { return owner(owner.toString(), "MANAGER").path("gold").asLong(); }
    public static synchronized void gold(UUID owner, long value) { owner(owner.toString(), "MANAGER").put("gold", value); }
    public static synchronized void runeQuantity(UUID owner, UUID rune, int value) {
        quantity(owner(owner.toString(), owner.toString().contains("-0001-") ? "AGENCY" : "MANAGER"), "runes", "rune", rune.toString()).put("quantity", value);
    }
    public static synchronized JsonNode rune(UUID id) { return fixture.path("catalogRunes").path(id.toString()).deepCopy(); }
    @Override public Map<String, String> start() {
        reset();
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/v1/assets/core", this::respond);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); server.start();
            return Map.of("hero-association.assets.base-url", "http://127.0.0.1:" + server.getAddress().getPort(),
                          "hero-association.assets.core-service-key", KEY);
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    @Override public void stop() { if (server != null) server.stop(0); }
    private void respond(HttpExchange exchange) throws IOException {
        int status = 200;
        JsonNode result;
        synchronized (AssetsStubResource.class) {
            if (!KEY.equals(exchange.getRequestHeaders().getFirst("X-Hero-Association-Assets-Core-Service-Key"))) {
                status = 403; result = JSON.createObjectNode();
            } else if (unavailable) { status = 503; result = JSON.createObjectNode(); }
            else {
                JsonNode body = JSON.readTree(exchange.getRequestBody().readAllBytes());
                if (exchange.getRequestURI().getPath().endsWith("/snapshots")) result = snapshot(body);
                else if (exchange.getRequestURI().getPath().endsWith("/commands")) {
                    result = command(body);
                    if (loseNextResponse) { loseNextResponse = false; status = 503; result = JSON.createObjectNode(); }
                    if (corruptNextReceipt) { corruptNextReceipt = false; ((ObjectNode) result).put("kind", "INCORRECT"); }
                } else result = receipts.getOrDefault(exchange.getRequestURI().getPath().replaceAll(".*/", ""), JSON.createObjectNode());
            }
        }
        byte[] bytes = JSON.writeValueAsBytes(result);
        exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes); exchange.close();
    }
    private static ObjectNode owner(String id, String type) {
        for (JsonNode value : fixture.withArray("owners")) if (id.equals(value.path("ownerId").asText())) return (ObjectNode) value;
        ObjectNode result = fixture.withArray("owners").addObject().put("ownerId", id).put("ownerType", type).put("gold", 0);
        result.putArray("items"); result.putArray("runes"); return result;
    }
    private static JsonNode snapshot(JsonNode request) {
        ObjectNode result = JSON.createObjectNode(); ArrayNode owners = result.putArray("owners");
        request.path("owners").forEach(value -> owners.add(owner(value.path("ownerId").asText(), value.path("ownerType").asText()).deepCopy()));
        result.set("heroes", loadouts(request.path("heroes"))); return result;
    }
    private static ObjectNode loadouts(JsonNode heroes) {
        ObjectNode result = JSON.createObjectNode();
        heroes.forEach(hero -> result.set(hero.asText(), fixture.path("heroes").has(hero.asText()) ? fixture.path("heroes").path(hero.asText()).deepCopy() : JSON.createArrayNode()));
        return result;
    }
    private static ObjectNode quantity(ObjectNode owner, String collection, String type, String id) {
        for (JsonNode row : owner.withArray(collection)) if (id.equals(row.path(type).path("id").asText())) return (ObjectNode) row;
        ObjectNode row = owner.withArray(collection).addObject().put("quantity", 0);
        row.set(type, fixture.path(type.equals("rune") ? "catalogRunes" : "catalogItems").path(id).deepCopy()); return row;
    }
    private static JsonNode command(JsonNode request) {
        String key = request.path("operationKey").asText();
        if (receipts.containsKey(key)) return receipts.get(key).deepCopy();
        ObjectNode result = JSON.createObjectNode().put("operationKey", key).put("kind", request.path("kind").asText()).put("status", "APPLIED");
        result.putNull("rejectionStatus"); result.putNull("message"); result.set("request", request.deepCopy());
        ObjectNode manager = owner(request.path("managerId").asText(), "MANAGER");
        switch (request.path("kind").asText()) {
            case "QUEST_START" -> {
                long fee = request.path("feeGold").asLong();
                if (manager.path("gold").asLong() < fee) result.put("status", "REJECTED").put("rejectionStatus", 409).put("message", "The Manager does not have enough gold for the borrowing fee.");
                else {
                    manager.put("gold", manager.path("gold").asLong() - fee);
                    ObjectNode agency = owner(request.path("agencyId").asText(), "AGENCY"); agency.put("gold", agency.path("gold").asLong() + fee);
                }
                result.set("heroes", loadouts(request.path("heroIds")));
            }
            case "HERO_LOADOUT_SNAPSHOT" -> result.set("heroes", loadouts(request.path("heroIds")));
            case "RUNE_EQUIP", "RUNE_UNEQUIP" -> {
                String hero = request.path("heroId").asText(); int index = request.path("slotIndex").asInt();
                ArrayNode slots = ((ObjectNode) fixture.path("heroes")).withArray(hero);
                JsonNode previous = null;
                for (int i = slots.size() - 1; i >= 0; i--) if (slots.get(i).path("slotIndex").asInt() == index) previous = slots.remove(i).path("rune");
                if (previous != null) { ObjectNode row = quantity(manager, "runes", "rune", previous.path("id").asText()); row.put("quantity", row.path("quantity").asInt() + 1); }
                if ("RUNE_EQUIP".equals(request.path("kind").asText())) {
                    String id = request.path("runeId").asText();
                    ObjectNode source = "AGENCY".equals(request.path("sourceOwnerType").asText()) ? owner(request.path("agencyId").asText(), "AGENCY") : manager;
                    ObjectNode row = quantity(source, "runes", "rune", id);
                    if (row.path("quantity").asInt() < 1) {
                        result.put("status", "REJECTED").put("rejectionStatus", 404).put("message", "The requested rune is unavailable in that inventory.");
                    } else {
                        row.put("quantity", row.path("quantity").asInt() - 1);
                        slots.addObject().put("slotIndex", index).set("rune", fixture.path("catalogRunes").path(id).deepCopy());
                    }
                }
                result.set("heroes", loadouts(JSON.createArrayNode().add(hero)));
            }
            case "EXPEDITION_CREDIT" -> {
                manager.put("gold", manager.path("gold").asLong() + request.path("gold").asLong());
                for (String type : List.of("items", "runes")) request.path(type).fields().forEachRemaining(entry -> {
                    ObjectNode row = quantity(manager, type, type.equals("items") ? "item" : "rune", entry.getKey());
                    row.put("quantity", row.path("quantity").asInt() + entry.getValue().asInt());
                });
                result.putObject("heroes");
            }
            default -> throw new IllegalArgumentException("Unexpected Assets contract command");
        }
        if (!result.path("status").asText().equals("REJECTED")) appliedCommands++;
        receipts.put(key, result.deepCopy()); return result;
    }
}
