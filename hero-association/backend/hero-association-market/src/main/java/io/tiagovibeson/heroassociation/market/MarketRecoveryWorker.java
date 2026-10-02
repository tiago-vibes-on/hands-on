package io.tiagovibeson.heroassociation.market;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class MarketRecoveryWorker {
    @Inject MarketCoordinator coordinator;
    @Inject MarketTransactions transactions;
    @Inject MeterRegistry metrics;
    private final Map<String, AtomicLong> pending = new HashMap<>();
    private final Map<String, AtomicLong> oldest = new HashMap<>();
    void start(@Observes StartupEvent event) {
        for (String kind : new String[]{"placement", "trade", "cancellation"}) {
            AtomicLong gauge = new AtomicLong(); pending.put(kind, gauge);
            AtomicLong age = new AtomicLong(); oldest.put(kind, age);
            metrics.gauge("market.pending.oldest.seconds", io.micrometer.core.instrument.Tags.of("kind", kind), age);
            metrics.gauge("market.pending.operations", io.micrometer.core.instrument.Tags.of("kind", kind), gauge);
        }
    }
    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void recover() {
        coordinator.recover();
        pending.forEach((kind, gauge) -> gauge.set(transactions.pendingCount(kind)));
        oldest.forEach((kind, gauge) -> gauge.set(transactions.oldestPendingSeconds(kind, java.time.Instant.now())));
    }
}
