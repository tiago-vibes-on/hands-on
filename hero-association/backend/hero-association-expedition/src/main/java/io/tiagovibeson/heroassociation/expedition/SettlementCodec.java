package io.tiagovibeson.heroassociation.expedition;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SettlementCodec {

    private final ObjectMapper mapper;

    public SettlementCodec(ObjectMapper mapper) {
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false);
    }

    public byte[] encode(SettlementEnvelope envelope) {
        try {
            ObjectNode payload = mapper.valueToTree(envelope);
            if (envelope.quest() != null) {
                // Set iteration order changes between JVMs; retries must retain identical wire bytes.
                var mapIds = mapper.createArrayNode();
                envelope.quest().pin().assignment().definition().mapIds().stream()
                        .map(java.util.UUID::toString).sorted().forEach(mapIds::add);
                ((ObjectNode) payload.path("quest").path("pin").path("assignment").path("definition"))
                        .set("mapIds", mapIds);
            }
            return mapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode Expedition settlement.", exception);
        }
    }

    public String digest(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
