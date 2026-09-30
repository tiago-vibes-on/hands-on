package io.tiagovibeson.heroassociation.application.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.domain.CombatProgressionInboxRecord;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CombatInboxServiceTest {

    @Inject
    CombatInboxService inbox;

    @Inject
    EntityManager entityManager;

    @Inject
    ObjectMapper mapper;

    @Test
    void shouldStoreOnceAndLeaveFactsUnapplied() throws Exception {
        UUID batchId = UuidV7.next();
        UUID battleId = UuidV7.next();
        byte[] body = CombatInboxTestMessages.batch(
                mapper, batchId, battleId, 1, "STAMINA_ELAPSED");

        assertEquals(CombatInboxService.Result.STORED,
                inbox.receive(body, batchId.toString(), "application/json"));
        assertEquals(CombatInboxService.Result.DUPLICATE,
                inbox.receive(body, batchId.toString(), "application/json"));

        entityManager.clear();
        CombatProgressionInboxRecord stored =
                entityManager.find(CombatProgressionInboxRecord.class, batchId);
        assertEquals(battleId, stored.getBattleId());
        assertEquals(1, stored.getFirstSequence());
        assertEquals(1, stored.getLastSequence());
        assertEquals(new String(body, StandardCharsets.UTF_8), stored.getPayloadJson());
        assertNull(stored.getAppliedAt());
    }

    @Test
    void shouldStoreGapWithoutApplyingIt() throws Exception {
        UUID battleId = UuidV7.next();
        UUID laterId = UuidV7.next();
        byte[] later = CombatInboxTestMessages.batch(
                mapper, laterId, battleId, 4, "HERO_ACTION");
        UUID firstId = UuidV7.next();
        byte[] first = CombatInboxTestMessages.batch(
                mapper, firstId, battleId, 1, "STAMINA_ELAPSED");

        assertEquals(CombatInboxService.Result.STORED,
                inbox.receive(later, laterId.toString(), "application/json"));
        assertEquals(CombatInboxService.Result.STORED,
                inbox.receive(first, firstId.toString(), "application/json"));
        assertNull(entityManager.find(CombatProgressionInboxRecord.class, laterId).getAppliedAt());
        assertNull(entityManager.find(CombatProgressionInboxRecord.class, firstId).getAppliedAt());
    }

    @Test
    void shouldRejectConflictingReplay() throws Exception {
        UUID batchId = UuidV7.next();
        UUID battleId = UuidV7.next();
        byte[] first = CombatInboxTestMessages.batch(
                mapper, batchId, battleId, 1, "STAMINA_ELAPSED");
        byte[] changed = CombatInboxTestMessages.batch(
                mapper, batchId, battleId, 1, "HERO_ACTION");

        inbox.receive(first, batchId.toString(), "application/json");
        assertThrows(IllegalArgumentException.class,
                () -> inbox.receive(changed, batchId.toString(), "application/json"));
        assertEquals(new String(first, StandardCharsets.UTF_8),
                entityManager.find(CombatProgressionInboxRecord.class, batchId).getPayloadJson());
    }

    @Test
    void shouldRejectUnsupportedOrNoncontiguousEnvelope() throws Exception {
        UUID batchId = UuidV7.next();
        UUID battleId = UuidV7.next();
        byte[] valid = CombatInboxTestMessages.batch(
                mapper, batchId, battleId, 1, "STAMINA_ELAPSED");
        ObjectNode node = (ObjectNode) mapper.readTree(valid);

        node.put("schemaVersion", 2);
        assertThrows(IllegalArgumentException.class,
                () -> inbox.receive(bytes(node), batchId.toString(), "application/json"));
        node.put("schemaVersion", 1);
        node.put("lastSequence", 2);
        assertThrows(IllegalArgumentException.class,
                () -> inbox.receive(bytes(node), batchId.toString(), "application/json"));
        node.put("lastSequence", 1);
        assertThrows(IllegalArgumentException.class,
                () -> inbox.receive(bytes(node), UuidV7.next().toString(), "application/json"));
        node.put("batchId", UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class,
                () -> inbox.receive(bytes(node), UUID.randomUUID().toString(), "application/json"));
        assertNull(entityManager.find(CombatProgressionInboxRecord.class, batchId));
    }

    private byte[] bytes(ObjectNode node) {
        try {
            return mapper.writeValueAsBytes(node);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
