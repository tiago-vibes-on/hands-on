package io.tiagovibeson.heroassociation.combat.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.combat.domain.ProgressionFact;

public record ProgressionBatchMessage(
        int schemaVersion,
        UUID batchId,
        UUID battleId,
        long firstSequence,
        long lastSequence,
        Instant createdAt,
        List<ProgressionFact> facts) {

    public ProgressionBatchMessage {
        facts = List.copyOf(facts);
        if (schemaVersion != 1 || facts.isEmpty()
                || firstSequence <= 0 || lastSequence < firstSequence
                || facts.size() != lastSequence - firstSequence + 1) {
            throw new IllegalArgumentException("Invalid progression batch envelope.");
        }
        for (int index = 0; index < facts.size(); index++) {
            if (facts.get(index).sequence() != firstSequence + index) {
                throw new IllegalArgumentException("Progression facts must be contiguous.");
            }
        }
    }
}
