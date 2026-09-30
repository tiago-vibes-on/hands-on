package io.tiagovibeson.heroassociation.expedition;

import java.io.IOException;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

/** A Core acknowledgment is valid only for the exact still-frozen aggregate. */
@ApplicationScoped
public class SettlementAckHandler {

    private final RedisRunStore runs;
    private final RedisSettlementStore settlements;
    private final SettlementCodec codec;
    private final ObjectMapper mapper;

    public SettlementAckHandler(RedisRunStore runs, RedisSettlementStore settlements,
                                SettlementCodec codec, ObjectMapper mapper) {
        this.runs = runs;
        this.settlements = settlements;
        this.codec = codec;
        this.mapper = mapper;
    }

    public boolean accept(byte[] body, String messageId, String contentType) {
        if (body == null || body.length == 0 || body.length > 4096
                || !"application/json".equals(contentType)) {
            throw new IllegalArgumentException("Invalid owner acknowledgment envelope.");
        }
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid owner acknowledgment JSON.", exception);
        }
        if (root == null || !root.isObject() || root.path("schemaVersion").asInt(-1) != 1) {
            throw new IllegalArgumentException("Unsupported owner acknowledgment schema.");
        }
        UUID expeditionId = uuid(root.path("expeditionId"));
        UUID managerId = uuid(root.path("ownerManagerId"));
        if (!expeditionId.toString().equals(messageId)) {
            throw new IllegalArgumentException("Owner acknowledgment message ID disagrees with the run.");
        }
        String digest = root.path("digest").asText();
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid owner acknowledgment digest.");
        }
        RunState run = runs.get(managerId, expeditionId);
        if (run == null) {
            if (digest.equals(settlements.closedDigest(managerId, expeditionId))) {
                return false;
            }
            throw new IllegalStateException("No active run or closure receipt for owner acknowledgment.");
        }
        byte[] frozen = codec.encode(SettlementEnvelope.from(run));
        if (!digest.equals(codec.digest(frozen))) {
            throw new IllegalArgumentException("Owner acknowledgment does not match the frozen settlement.");
        }
        if (!digest.equals(settlements.publishedDigest(managerId))) {
            throw new IllegalStateException("Owner acknowledgment arrived before broker confirmation was retained.");
        }
        return settlements.complete(run, digest);
    }

    private UUID uuid(JsonNode value) {
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Owner acknowledgment ID is missing.");
        }
        UUID id = UUID.fromString(value.textValue());
        if (id.version() != 7 || !id.toString().equals(value.textValue())) {
            throw new IllegalArgumentException("Owner acknowledgment requires UUIDv7.");
        }
        return id;
    }
}
