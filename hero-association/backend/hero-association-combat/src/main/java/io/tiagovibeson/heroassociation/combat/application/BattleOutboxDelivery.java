package io.tiagovibeson.heroassociation.combat.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tiagovibeson.heroassociation.combat.domain.BattleProgressionBatchRecord;
import io.tiagovibeson.heroassociation.combat.domain.ProgressionFact;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BattleOutboxDelivery {

    private static final String NEXT_BATCH_SQL = """
            SELECT batch.id
            FROM battle_progression_batch batch
            WHERE batch.published_at IS NULL
              AND NOT EXISTS (
                SELECT 1 FROM battle_progression_batch earlier
                WHERE earlier.battle_id = batch.battle_id
                  AND earlier.published_at IS NULL
                  AND earlier.first_sequence < batch.first_sequence
              )
            ORDER BY batch.created_at, batch.id
            LIMIT 1
            FOR UPDATE OF batch SKIP LOCKED
            """;

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final ProgressionBatchTransport transport;

    public BattleOutboxDelivery(
            EntityManager entityManager, ObjectMapper objectMapper, ProgressionBatchTransport transport) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.transport = transport;
    }

    @Transactional
    public boolean publishOne() {
        @SuppressWarnings("unchecked")
        List<UUID> ids = entityManager.createNativeQuery(NEXT_BATCH_SQL)
                .getResultList();
        if (ids.isEmpty()) {
            return false;
        }
        BattleProgressionBatchRecord batch =
                entityManager.find(BattleProgressionBatchRecord.class, ids.getFirst());
        if (batch.getPublishedAt() != null) {
            return false;
        }
        try {
            List<ProgressionFact> facts = objectMapper.readValue(
                    batch.getFactsJson(), new TypeReference<>() {});
            transport.publish(new ProgressionBatchMessage(
                    1, batch.getId(), batch.getBattleId(),
                    batch.getFirstSequence(), batch.getLastSequence(),
                    batch.getCreatedAt(), facts));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored progression batch is invalid.", exception);
        }
        batch.markPublished(Instant.now());
        return true;
    }
}
