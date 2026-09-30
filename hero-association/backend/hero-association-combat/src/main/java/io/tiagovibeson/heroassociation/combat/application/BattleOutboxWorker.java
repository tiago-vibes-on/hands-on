package io.tiagovibeson.heroassociation.combat.application;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BattleOutboxWorker {

    private static final Logger LOG = Logger.getLogger(BattleOutboxWorker.class);
    private static final int MAX_BATCHES_PER_TICK = 100;

    private final BattleOutboxDelivery delivery;

    @ConfigProperty(name = "hero-association.combat.outbox.enabled", defaultValue = "false")
    boolean enabled;

    public BattleOutboxWorker(BattleOutboxDelivery delivery) {
        this.delivery = delivery;
    }

    @Scheduled(every = "1s", delayed = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void publishDue() {
        if (!enabled) {
            return;
        }
        publishPending();
    }

    public int publishPending() {
        int count = 0;
        while (count < MAX_BATCHES_PER_TICK) {
            try {
                if (!delivery.publishOne()) {
                    break;
                }
                count++;
            } catch (RuntimeException exception) {
                LOG.error("Combat progression outbox delivery failed; the batch remains pending.", exception);
                break;
            }
        }
        return count;
    }
}
