package io.tiagovibeson.heroassociation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.application.ScheduledJobLock.Job;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@QuarkusTest
class ScheduledJobLockTest {

    private static final UUID RECOVERING_HERO_ID = UUID.fromString("019c4c00-0010-7000-8000-000000000004");
    private static final UUID ACTIVE_COMBAT_ID = UUID.fromString("019c4c00-0050-7000-8000-000000000001");

    @Inject
    ScheduledJobLock scheduledJobLock;

    @Inject
    AgencyRecoveryScheduler agencyRecoveryScheduler;

    @Inject
    QuestCombatScheduler questCombatScheduler;

    @Inject
    HeroRepository heroRepository;

    @Inject
    EntityManager entityManager;

    @Test
    @Timeout(30)
    void shouldAllowOnlyOneTransactionPerJobAndReleaseItsLockAtCommit() throws Exception {
        for (Job job : Job.values()) {
            whileLocked(job, () -> {
                assertFalse(QuarkusTransaction.requiringNew().call(() -> scheduledJobLock.tryAcquire(job)));
                Job otherJob = job == Job.AGENCY_RECOVERY ? Job.QUEST_COMBAT : Job.AGENCY_RECOVERY;
                assertTrue(QuarkusTransaction.requiringNew().call(() -> scheduledJobLock.tryAcquire(otherJob)));
            });
            assertTrue(QuarkusTransaction.requiringNew().call(() -> scheduledJobLock.tryAcquire(job)));
        }
    }

    @Test
    @Timeout(30)
    void shouldReleaseTheLockWhenTheOwningTransactionRollsBack() {
        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            assertTrue(scheduledJobLock.tryAcquire(Job.AGENCY_RECOVERY));
            throw new IllegalStateException("rollback");
        }));
        assertTrue(QuarkusTransaction.requiringNew().call(() -> scheduledJobLock.tryAcquire(Job.AGENCY_RECOVERY)));
    }

    @Test
    @TestTransaction
    @Timeout(30)
    void shouldSkipRecoveryWhenAnotherTransactionOwnsTheRecoveryJob() throws Exception {
        entityManager.createNativeQuery("""
                UPDATE hero
                SET current_health = 10,
                    current_mana = 10,
                    activity = 'TRAINING',
                    last_resource_synchronized_at = :synchronizedAt
                WHERE id = :heroId
                """)
                .setParameter("synchronizedAt", Timestamp.from(Instant.now().minusSeconds(2)))
                .setParameter("heroId", RECOVERING_HERO_ID)
                .executeUpdate();
        entityManager.clear();

        whileLocked(Job.AGENCY_RECOVERY, () -> {
            agencyRecoveryScheduler.recoverAgencyHeroes();
            entityManager.clear();
            Hero hero = heroRepository.findById(RECOVERING_HERO_ID);
            assertEquals(10, hero.getCurrentHealth());
            assertEquals(10, hero.getCurrentMana());
        });

        agencyRecoveryScheduler.recoverAgencyHeroes();
        entityManager.flush();
        entityManager.clear();
        Hero hero = heroRepository.findById(RECOVERING_HERO_ID);
        assertTrue(hero.getCurrentHealth() > 10);
        assertTrue(hero.getCurrentMana() > 10);
    }

    @Test
    @TestTransaction
    @Timeout(30)
    void shouldSkipCombatWhenAnotherTransactionOwnsTheCombatJob() throws Exception {
        entityManager.createNativeQuery("""
                UPDATE quest_combat
                SET current_time_milliseconds = 0,
                    last_synchronized_at = :synchronizedAt
                WHERE id = :combatId
                """)
                .setParameter("synchronizedAt", Timestamp.from(Instant.now().minusSeconds(2)))
                .setParameter("combatId", ACTIVE_COMBAT_ID)
                .executeUpdate();
        entityManager.clear();

        whileLocked(Job.QUEST_COMBAT, () -> {
            questCombatScheduler.synchronizeActiveCombats();
            assertEquals(0, combatTimeMilliseconds());
        });

        questCombatScheduler.synchronizeActiveCombats();
        entityManager.flush();
        assertTrue(combatTimeMilliseconds() > 0);
    }

    private long combatTimeMilliseconds() {
        Number value = (Number) entityManager.createNativeQuery("""
                SELECT current_time_milliseconds
                FROM quest_combat
                WHERE id = :combatId
                """)
                .setParameter("combatId", ACTIVE_COMBAT_ID)
                .getSingleResult();
        return value.longValue();
    }

    private void whileLocked(Job job, CheckedRunnable assertion) throws Exception {
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> holder = executor.submit(() -> QuarkusTransaction.requiringNew().run(() -> {
            assertTrue(scheduledJobLock.tryAcquire(job));
            acquired.countDown();
            try {
                assertTrue(release.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }));

        try {
            assertTrue(acquired.await(10, TimeUnit.SECONDS));
            assertion.run();
        } finally {
            release.countDown();
            try {
                holder.get(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
