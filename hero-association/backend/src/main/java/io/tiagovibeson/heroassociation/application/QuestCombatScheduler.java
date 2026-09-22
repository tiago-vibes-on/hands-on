package io.tiagovibeson.heroassociation.application;

import java.time.Instant;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.tiagovibeson.heroassociation.repository.QuestCombatRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class QuestCombatScheduler {

    @Inject
    QuestCombatRepository questCombatRepository;

    @Inject
    QuestCombatProgressionService questCombatProgressionService;

    @Scheduled(every = "5s", delayed = "5s", concurrentExecution = ConcurrentExecution.SKIP)
    @Transactional
    void synchronizeActiveCombats() {
        Instant synchronizedAt = Instant.now();
        questCombatRepository.listInProgressForUpdate()
                .forEach(combat -> questCombatProgressionService.synchronize(combat, synchronizedAt));
    }
}
