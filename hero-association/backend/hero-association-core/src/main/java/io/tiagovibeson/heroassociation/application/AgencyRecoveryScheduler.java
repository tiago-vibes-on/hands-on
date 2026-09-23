package io.tiagovibeson.heroassociation.application;

import java.time.Instant;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AgencyRecoveryScheduler {

    @Inject
    AgencyRecoveryService agencyRecoveryService;

    @Scheduled(every = "5s", delayed = "5s", concurrentExecution = ConcurrentExecution.SKIP)
    void recoverAgencyHeroes() {
        agencyRecoveryService.recoverAt(Instant.now());
    }
}
