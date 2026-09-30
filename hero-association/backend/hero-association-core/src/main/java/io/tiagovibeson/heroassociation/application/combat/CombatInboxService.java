package io.tiagovibeson.heroassociation.application.combat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tiagovibeson.heroassociation.domain.CombatProgressionInboxRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CombatInboxService {

    private static final int MAX_BODY_BYTES = 1_048_576;
    private static final int MAX_FACTS = 10_000;
    private static final Set<String> FACT_TYPES = Set.of(
            "STAMINA_ELAPSED", "HERO_ACTION", "CREATURE_KILLED", "HERO_FELL", "BATTLE_COMPLETED");

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public CombatInboxService(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    public enum Result {
        STORED,
        DUPLICATE
    }

    @Transactional
    public Result receive(byte[] body, String messageId, String contentType) {
        Envelope envelope = parse(body, messageId, contentType);
        CombatProgressionInboxRecord existing =
                entityManager.find(CombatProgressionInboxRecord.class, envelope.batchId());
        if (existing != null) {
            if (!existing.getPayloadJson().equals(envelope.payloadJson())) {
                throw new IllegalArgumentException("Batch ID was reused with a different payload.");
            }
            return Result.DUPLICATE;
        }
        entityManager.persist(new CombatProgressionInboxRecord(
                envelope.batchId(), envelope.battleId(), envelope.firstSequence(),
                envelope.lastSequence(), envelope.payloadJson(), Instant.now()));
        entityManager.flush();
        return Result.STORED;
    }

    private Envelope parse(byte[] body, String messageId, String contentType) {
        if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES
                || !"application/json".equals(contentType)) {
            throw new IllegalArgumentException("Combat batch must be bounded application/json.");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Combat batch JSON is invalid.", exception);
        }
        if (root == null || !root.isObject() || !root.path("schemaVersion").isInt()
                || root.path("schemaVersion").intValue() != 1) {
            throw new IllegalArgumentException("Unsupported Combat batch schema.");
        }
        UUID batchId = uuidV7(root.path("batchId"));
        UUID battleId = uuidV7(root.path("battleId"));
        if (!batchId.toString().equals(messageId)) {
            throw new IllegalArgumentException("AMQP message ID does not match the batch ID.");
        }
        JsonNode first = root.path("firstSequence");
        JsonNode last = root.path("lastSequence");
        if (!first.isIntegralNumber() || !last.isIntegralNumber()
                || !first.canConvertToLong() || !last.canConvertToLong()) {
            throw new IllegalArgumentException("Combat batch sequences are invalid.");
        }
        long firstSequence = first.longValue();
        long lastSequence = last.longValue();
        if (firstSequence <= 0 || lastSequence < firstSequence
                || lastSequence - firstSequence >= MAX_FACTS) {
            throw new IllegalArgumentException("Combat batch sequence range is invalid.");
        }
        try {
            Instant.parse(requiredText(root.path("createdAt")));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Combat batch creation time is invalid.", exception);
        }
        JsonNode facts = root.path("facts");
        if (!facts.isArray() || facts.size() != lastSequence - firstSequence + 1) {
            throw new IllegalArgumentException("Combat batch facts are not contiguous.");
        }
        long priorTime = -1;
        for (int index = 0; index < facts.size(); index++) {
            JsonNode fact = facts.get(index);
            JsonNode sequence = fact.path("sequence");
            JsonNode occurredAt = fact.path("occurredAtMilliseconds");
            if (!fact.isObject() || !sequence.isIntegralNumber()
                    || !sequence.canConvertToLong() || sequence.longValue() != firstSequence + index
                    || !occurredAt.isIntegralNumber() || !occurredAt.canConvertToLong()
                    || occurredAt.longValue() < 0
                    || occurredAt.longValue() < priorTime
                    || !FACT_TYPES.contains(requiredText(fact.path("type")))) {
                throw new IllegalArgumentException("Combat batch contains an invalid fact.");
            }
            priorTime = occurredAt.longValue();
        }
        return new Envelope(batchId, battleId, firstSequence, lastSequence,
                new String(body, StandardCharsets.UTF_8));
    }

    private UUID uuidV7(JsonNode value) {
        String text = requiredText(value);
        try {
            UUID uuid = UUID.fromString(text);
            if (uuid.version() == 7 && uuid.toString().equals(text)) {
                return uuid;
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Combat batch identity is invalid.", exception);
        }
        throw new IllegalArgumentException("Combat batch identity must be UUIDv7.");
    }

    private String requiredText(JsonNode value) {
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Combat batch has a missing text field.");
        }
        return value.textValue();
    }

    private record Envelope(
            UUID batchId, UUID battleId, long firstSequence, long lastSequence, String payloadJson) {
    }
}
