package io.tiagovibeson.heroassociation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AgencyRecoveryServiceTest {

    private static final UUID OAKSHIELD_HERO_ID = UUID.fromString("019c4c00-0010-7000-8000-000000000004");
    private static final UUID EMBERVEIL_HERO_ID = UUID.fromString("019c4c00-0010-7000-8000-000000000005");

    @Inject
    AgencyRecoveryService agencyRecoveryService;

    @Inject
    HeroRepository heroRepository;

    @Inject
    EntityManager entityManager;

    @Test
    @Transactional
    void shouldRecoverTrainingAtTheBaseRateAndRestingAtTwiceTheBaseRate() {
        Instant synchronizedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        seedRecoveringHero(OAKSHIELD_HERO_ID, HeroActivity.TRAINING, synchronizedAt);
        seedRecoveringHero(EMBERVEIL_HERO_ID, HeroActivity.RESTING, synchronizedAt);
        entityManager.clear();

        agencyRecoveryService.recoverAt(synchronizedAt);
        entityManager.flush();
        entityManager.clear();

        Hero trainingHero = heroRepository.findById(OAKSHIELD_HERO_ID);
        Hero restingHero = heroRepository.findById(EMBERVEIL_HERO_ID);
        assertEquals(20, trainingHero.getCurrentHealth());
        assertEquals(12, trainingHero.getCurrentMana());
        assertEquals(14, restingHero.getCurrentHealth());
        assertEquals(30, restingHero.getCurrentMana());
    }

    private void seedRecoveringHero(UUID heroId, HeroActivity activity, Instant synchronizedAt) {
        entityManager.createNativeQuery("""
                UPDATE hero
                SET current_health = 10,
                    current_mana = 10,
                    activity = :activity,
                    last_resource_synchronized_at = :lastSynchronizedAt
                WHERE id = :heroId
                """)
                .setParameter("activity", activity.name())
                .setParameter("lastSynchronizedAt", Timestamp.from(synchronizedAt.minusSeconds(1)))
                .setParameter("heroId", heroId)
                .executeUpdate();
    }
}
