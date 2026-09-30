package io.tiagovibeson.heroassociation.expedition;

import java.time.Instant;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.tiagovibeson.heroassociation.expedition.RedisSettlementStore.Member;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/** Retries the frozen aggregate until Core's separate owner-applied acknowledgment arrives. */
@ApplicationScoped
public class SettlementWorker {

    private static final Logger LOG = Logger.getLogger(SettlementWorker.class);
    private static final int MAX_DUE_PER_TICK = 64;

    private final RedisRunStore runs;
    private final RedisSettlementStore settlements;
    private final SettlementCodec codec;
    private final SettlementTransport transport;

    @ConfigProperty(name = "expedition.settlement.enabled", defaultValue = "false")
    boolean enabled;

    public SettlementWorker(RedisRunStore runs, RedisSettlementStore settlements,
                            SettlementCodec codec, SettlementTransport transport) {
        this.runs = runs;
        this.settlements = settlements;
        this.codec = codec;
        this.transport = transport;
    }

    void onStart(@Observes StartupEvent event) {
        if (enabled) {
            settlements.rebuildDueIndex();
        }
    }

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledTick() {
        if (enabled) {
            tick(Instant.now());
        }
    }

    public int tick(Instant now) {
        int confirmed = 0;
        for (Member member : settlements.due(now, MAX_DUE_PER_TICK)) {
            String token = UUID.randomUUID().toString();
            if (!settlements.claim(member, token)) {
                continue;
            }
            try {
                // Bounded retry; broker confirmation is not Core application.
                settlements.schedule(member, now.plusSeconds(10));
                RunState run = runs.get(member.managerId(), member.expeditionId());
                if (run == null || run.phase() != RunState.Phase.SETTLEMENT_PENDING) {
                    settlements.unschedule(member);
                    continue;
                }
                byte[] body = codec.encode(SettlementEnvelope.from(run));
                transport.publish(body, run.expeditionId());
                settlements.confirmed(run, codec.digest(body));
                confirmed++;
            } catch (RuntimeException exception) {
                LOG.errorf(exception, "Settlement %s remains pending for retry", member.expeditionId());
                settlements.schedule(member, now.plusSeconds(10));
            } finally {
                settlements.release(member, token);
            }
        }
        return confirmed;
    }
}
