package io.tiagovibeson.heroassociation.expedition;

import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/** Coordinates Core's durable reservation with Redis run creation. */
@ApplicationScoped
public class ExpeditionEntryService {

    private static final Logger LOG = Logger.getLogger(ExpeditionEntryService.class);

    private final CoreAdmissionClient core;
    private final ExpeditionService expeditions;
    private final RedisRunStore runs;
    @jakarta.inject.Inject QuestClient quests;

    @ConfigProperty(name = "hero-association.expedition.admission-reconciler.enabled", defaultValue = "false")
    boolean reconcilerEnabled;

    public ExpeditionEntryService(CoreAdmissionClient core, ExpeditionService expeditions, RedisRunStore runs) {
        this.core = core;
        this.expeditions = expeditions;
        this.runs = runs;
    }

    /** The player token is forwarded only to Core; Core resolves and reserves the Manager. */
    public RunState start(UUID expeditionId, UUID agencyId, UUID partyId, UUID mapId,
                          UUID commandId, String playerAccessToken) {
        PreparedEntry entry = core.reserve(expeditionId, agencyId, partyId, mapId, playerAccessToken);
        try {
            var pin = quests.pin(entry);
            entry = entry.withQuest(pin);
            return expeditions.startPrepared(entry, commandId);
        } catch (RuntimeException failure) {
            try {
                releaseIfAbsent(entry.ownerManagerId(), entry.expeditionId());
            } catch (RuntimeException reconciliationFailure) {
                failure.addSuppressed(reconciliationFailure);
            }
            throw failure;
        }
    }

    /** A cancelled Redis ID cannot be started later, even by a delayed request. */
    public boolean releaseIfAbsent(UUID managerId, UUID expeditionId) {
        if (!runs.cancelIfAbsent(managerId, expeditionId)) {
            return false;
        }
        boolean released = core.releaseProvenAbsent(expeditionId, managerId);
        quests.release(expeditionId, managerId);
        return released;
    }

    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void reconcileOrphans() {
        if (!reconcilerEnabled) {
            return;
        }
        try {
            for (CoreAdmissionClient.ReservationCandidate candidate : core.orphanCandidates()) {
                try {
                    releaseIfAbsent(candidate.ownerManagerId(), candidate.expeditionId());
                } catch (RuntimeException failure) {
                    LOG.warnf(failure, "Could not reconcile Expedition reservation %s", candidate.expeditionId());
                }
            }
            for (QuestClient.Orphan candidate : quests.orphans()) {
                try { releaseIfAbsent(candidate.ownerManagerId(), candidate.expeditionId()); }
                catch (RuntimeException failure) { LOG.warnf(failure, "Could not reconcile Quest admission %s", candidate.expeditionId()); }
            }
        } catch (RuntimeException failure) {
            LOG.warn("Core admission reconciliation is unavailable; reservations remain locked.", failure);
        }
    }
}
