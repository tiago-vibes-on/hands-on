package io.tiagovibeson.heroassociation.application.combat;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

final class CombatInboxTestMessages {

    private CombatInboxTestMessages() {
    }

    static byte[] batch(
            ObjectMapper mapper, UUID batchId, UUID battleId, long sequence, String factType) throws IOException {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("sequence", sequence);
        fact.put("occurredAtMilliseconds", 1_000);
        fact.put("type", factType);
        fact.put("heroIds", List.of(UUID.randomUUID().toString()));
        fact.put("heroId", null);
        fact.put("creatureCombatantId", null);
        fact.put("elapsedMilliseconds", 1_000);
        fact.put("action", null);
        fact.put("manaSpent", 0);
        fact.put("baseExperience", 0);
        fact.put("finalSnapshot", null);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("schemaVersion", 1);
        envelope.put("batchId", batchId);
        envelope.put("battleId", battleId);
        envelope.put("firstSequence", sequence);
        envelope.put("lastSequence", sequence);
        envelope.put("createdAt", Instant.now().toString());
        envelope.put("facts", List.of(fact));
        return mapper.writeValueAsBytes(envelope);
    }
}
