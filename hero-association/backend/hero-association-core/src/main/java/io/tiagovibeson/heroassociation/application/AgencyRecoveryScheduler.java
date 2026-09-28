package io.tiagovibeson.heroassociation.application;

import java.time.Instant;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AgencyRecoveryScheduler {

    @Inject
    AgencyRecoveryService agencyRecoveryService;

    @Inject
    ScheduledJobLock scheduledJobLock;

    @Scheduled(every = "5s", delayed = "5s", concurrentExecution = ConcurrentExecution.SKIP)
    @Transactional
    void recoverAgencyHeroes() {
        if (!scheduledJobLock.tryAcquire(ScheduledJobLock.Job.AGENCY_RECOVERY)) {
            return;
        }
        agencyRecoveryService.recoverAt(Instant.now());
    }
}
