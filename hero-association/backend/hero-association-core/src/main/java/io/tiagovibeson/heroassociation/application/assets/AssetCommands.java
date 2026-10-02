package io.tiagovibeson.heroassociation.application.assets;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class AssetCommands {
    private AssetCommands() { }
    public static ObjectNode base(ObjectMapper mapper, UUID key, String kind, UUID manager) {
        ObjectNode command = mapper.createObjectNode();
        for (String name : List.of("operationKey", "kind", "managerId", "agencyId", "heroId", "slotIndex", "runeId", "sourceOwnerType", "feeGold", "heroIds", "gold", "items", "runes")) command.putNull(name);
        command.put("operationKey", key.toString()); command.put("kind", kind); command.put("managerId", manager.toString()); return command;
    }
    public static void validateReceipt(JsonNode expected, JsonNode receipt) {
        if (receipt == null || !expected.path("operationKey").equals(receipt.path("operationKey"))
                || !expected.path("kind").equals(receipt.path("kind")) || !expected.equals(receipt.path("request"))
                || !Set.of("APPLIED", "REJECTED").contains(receipt.path("status").asText()))
            throw new ProtocolConflict("Assets command receipt differs from the staged command.");
        if ("REJECTED".equals(receipt.path("status").asText()) && !Set.of(400, 403, 404, 409).contains(receipt.path("rejectionStatus").asInt()))
            throw new ProtocolConflict("Assets rejection receipt has an invalid status.");
    }
    public static class ProtocolConflict extends RuntimeException {
        public ProtocolConflict(String message) { super(message); }
    }
}
