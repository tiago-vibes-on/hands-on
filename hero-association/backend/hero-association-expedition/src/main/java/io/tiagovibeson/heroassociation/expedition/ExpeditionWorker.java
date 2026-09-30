package io.tiagovibeson.heroassociation.expedition;

import java.time.Instant;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.tiagovibeson.heroassociation.expedition.RedisRunStore.Member;
import io.tiagovibeson.heroassociation.expedition.RedisRunStore.Result;
import io.tiagovibeson.heroassociation.expedition.RunState.Phase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/** One bounded scheduler per replica; Redis leases and CAS fence competing workers. */
@ApplicationScoped
public class ExpeditionWorker {

    private static final Logger LOG = Logger.getLogger(ExpeditionWorker.class);
    private static final int MAX_DUE_PER_TICK = 64;
    private static final int MAX_COMMIT_RETRIES = 4;

    private final RedisRunStore store;
    private final RedisSettlementStore settlements;
    private final RedisFightTimelineStore timelines;
    private final FightResolver resolver;

    @ConfigProperty(name = "expedition.worker.enabled", defaultValue = "false")
    boolean enabled;

    public ExpeditionWorker(RedisRunStore store, RedisSettlementStore settlements,
                            RedisFightTimelineStore timelines, FightResolver resolver) {
        this.store = store;
        this.settlements = settlements;
        this.timelines = timelines;
        this.resolver = resolver;
    }

    void onStart(@Observes StartupEvent event) {
        if (enabled) {
            store.rebuildDueIndex();
        }
    }

    @Scheduled(every = "1s", concurrentExecution = ConcurrentExecution.SKIP)
    void scheduledTick() {
        if (enabled) {
            tick(Instant.now());
        }
    }

    /** Public for deterministic tests and explicit sandbox checks; no browser command calls it. */
    public int tick(Instant now) {
        int completed = 0;
        for (Member member : store.due(now, MAX_DUE_PER_TICK)) {
            if (process(member, now)) {
                completed++;
            }
        }
        return completed;
    }

    public int rebuildDueIndex() {
        return store.rebuildDueIndex();
    }

    private boolean process(Member member, Instant now) {
        String leaseToken = UUID.randomUUID().toString();
        if (!store.claim(member, leaseToken)) {
            return false;
        }
        try {
            // Hide this claimed item so other replicas can use the rest of the due batch.
            // This index is repairable; it is not the authoritative fight state.
            store.schedule(member, now.plusSeconds(30));
            RunState observed = store.get(member.managerId(), member.expeditionId());
            if (observed == null || observed.phase() != Phase.FIGHTING
                    || !observed.fight().fightId().equals(member.fightId())) {
                store.unschedule(member);
                return false;
            }
            FightTimeline timeline = timelines.get(observed);
            if (timeline == null) timeline = timelines.saveIfAbsent(observed, resolver.plan(observed));
            Instant finishesAt = observed.fight().startedAt().plusMillis(timeline.outcome().durationMilliseconds());
            if (finishesAt.isAfter(now)) {
                store.schedule(member, finishesAt);
                return false;
            }
            for (int attempt = 0; attempt < MAX_COMMIT_RETRIES; attempt++) {
                RunState current = store.get(member.managerId(), member.expeditionId());
                if (current == null || current.phase() != Phase.FIGHTING
                        || !current.fight().fightId().equals(member.fightId())) {
                    store.unschedule(member);
                    return false;
                }
                RunState updated = current.finish(timeline.heroes(), timeline.outcome());
                Result result = store.commitFight(current, updated, leaseToken);
                if (result == Result.UPDATED) {
                    store.unschedule(member);
                    timelines.remove(observed);
                    if (updated.phase() == Phase.SETTLEMENT_PENDING) {
                        settlements.schedule(updated, Instant.now());
                    }
                    return true;
                }
                if (result != Result.STALE) {
                    return false;
                }
            }
            store.schedule(member, now.plusSeconds(1));
            return false;
        } catch (RuntimeException exception) {
            LOG.errorf(exception, "Expedition fight %s could not advance; retaining its run", member.fightId());
            store.schedule(member, now.plusSeconds(30));
            return false;
        } finally {
            store.release(member, leaseToken);
        }
    }
}
