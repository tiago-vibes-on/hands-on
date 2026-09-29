package io.tiagovibeson.heroassociation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.HeroProgression;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AgencyRecoveryServiceTest {

    private static final UUID OAKSHIELD_HERO_ID = UUID.fromString("019c4c00-0010-7000-8000-000000000004");
    private static final UUID EMBERVEIL_HERO_ID = UUID.fromString("019c4c00-0010-7000-8000-000000000005");
    private static final UUID IRONRIDGE_PERSONAL_HERO_ID = UUID.fromString("019c4c00-0030-7001-8000-000000000205");
    private static final UUID SILVERKEEP_PERSONAL_HERO_ID = UUID.fromString("019c4c00-0030-7001-8000-000000000208");
    private static final long LOW_STAMINA = HeroProgression.MAX_STAMINA_MILLISECONDS - 100_000;

    @Inject
    AgencyRecoveryService agencyRecoveryService;

    @Inject
    HeroRepository heroRepository;

    @Inject
    EntityManager entityManager;

    @Test
    @TestTransaction
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
        assertEquals(LOW_STAMINA + 1_000, trainingHero.getStaminaMilliseconds());
        assertEquals(LOW_STAMINA + 2_400, restingHero.getStaminaMilliseconds());
    }

    @Test
    @TestTransaction
    void shouldUseManagersAgencyRestLevelForPersonalHeroes() {
        Instant synchronizedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        seedRecoveringHero(IRONRIDGE_PERSONAL_HERO_ID, HeroActivity.RESTING, synchronizedAt);
        seedRecoveringHero(SILVERKEEP_PERSONAL_HERO_ID, HeroActivity.RESTING, synchronizedAt);
        entityManager.clear();

        agencyRecoveryService.recoverAt(synchronizedAt);
        entityManager.flush();
        entityManager.clear();

        assertEquals(LOW_STAMINA + 2_200,
                heroRepository.findById(IRONRIDGE_PERSONAL_HERO_ID).getStaminaMilliseconds());
        assertEquals(LOW_STAMINA + 2_000,
                heroRepository.findById(SILVERKEEP_PERSONAL_HERO_ID).getStaminaMilliseconds());
    }

    @Test
    @TestTransaction
    void shouldClampAtFullStaminaAndNotRepeatRecovery() {
        Instant synchronizedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        seedRecoveringHero(EMBERVEIL_HERO_ID, HeroActivity.RESTING, synchronizedAt);
        entityManager.createNativeQuery("""
                UPDATE hero SET stamina_milliseconds = :stamina WHERE id = :heroId
                """)
                .setParameter("stamina", HeroProgression.MAX_STAMINA_MILLISECONDS - 1_000)
                .setParameter("heroId", EMBERVEIL_HERO_ID)
                .executeUpdate();
        entityManager.clear();

        agencyRecoveryService.recoverAt(synchronizedAt);
        agencyRecoveryService.recoverAt(synchronizedAt);
        agencyRecoveryService.recoverAt(synchronizedAt.plusMillis(999));
        entityManager.flush();
        entityManager.clear();

        assertEquals(HeroProgression.MAX_STAMINA_MILLISECONDS,
                heroRepository.findById(EMBERVEIL_HERO_ID).getStaminaMilliseconds());
    }

    @Test
    @TestTransaction
    void shouldNotRecoverHeroesOnQuest() {
        Instant synchronizedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        seedRecoveringHero(OAKSHIELD_HERO_ID, HeroActivity.ON_QUEST, synchronizedAt);
        entityManager.clear();
        agencyRecoveryService.recoverAt(synchronizedAt);
        entityManager.clear();
        assertEquals(LOW_STAMINA, heroRepository.findById(OAKSHIELD_HERO_ID).getStaminaMilliseconds());
        assertEquals(10, heroRepository.findById(OAKSHIELD_HERO_ID).getCurrentHealth());
        assertEquals(10, heroRepository.findById(OAKSHIELD_HERO_ID).getCurrentMana());
    }

    private void seedRecoveringHero(UUID heroId, HeroActivity activity, Instant synchronizedAt) {
        entityManager.createNativeQuery("""
                UPDATE hero
                SET current_health = 10,
                    current_mana = 10,
                    stamina_milliseconds = :stamina,
                    activity = :activity,
                    last_resource_synchronized_at = :lastSynchronizedAt
                WHERE id = :heroId
                """)
                .setParameter("activity", activity.name())
                .setParameter("stamina", LOW_STAMINA)
                .setParameter("lastSynchronizedAt", Timestamp.from(synchronizedAt.minusSeconds(1)))
                .setParameter("heroId", heroId)
                .executeUpdate();
    }
}
