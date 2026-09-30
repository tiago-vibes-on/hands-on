package io.tiagovibeson.heroassociation.combat.application;

import java.time.Instant;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BattleWorker {

    private static final Logger LOG = Logger.getLogger(BattleWorker.class);

    private final BattleCandidateRepository candidates;
    private final BattleAdvanceService advances;

    @ConfigProperty(name = "hero-association.combat.worker.enabled", defaultValue = "false")
    boolean enabled;

    public BattleWorker(BattleCandidateRepository candidates, BattleAdvanceService advances) {
        this.candidates = candidates;
        this.advances = advances;
    }

    @Scheduled(every = "1s", delayed = "1s", concurrentExecution = ConcurrentExecution.SKIP)
    void advanceActiveBattles() {
        if (enabled) {
            advanceDueAt(Instant.now());
        }
    }

    public int advanceDueAt(Instant now) {
        int advanced = 0;
        for (UUID battleId : candidates.dueIds(now)) {
            try {
                if (advances.advanceDue(battleId, now)) {
                    advanced++;
                }
            } catch (RuntimeException exception) {
                LOG.errorf(exception, "Could not advance sandbox battle %s", battleId);
            }
        }
        return advanced;
    }
}
