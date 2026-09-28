package io.tiagovibeson.heroassociation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.application.combat.CombatProgressionApplier;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroProgression;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.domain.Quest;
import io.tiagovibeson.heroassociation.domain.QuestCombat;
import io.tiagovibeson.heroassociation.domain.QuestCombatant;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import io.tiagovibeson.heroassociation.domain.combat.CombatAction;
import io.tiagovibeson.heroassociation.domain.combat.CombatEvent;
import io.tiagovibeson.heroassociation.domain.combat.CombatHit;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;
import io.tiagovibeson.heroassociation.domain.combat.CombatTeam;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class QuestCombatProgressionServiceTest {

    private static final UUID AGENCY_ID = UUID.fromString("019c4c00-0001-7000-8000-000000000001");

    @Inject
    QuestCombatProgressionService progressionService;

    @Inject
    EntityManager entityManager;

    @Test
    @TestTransaction
    void shouldDrainStaminaEvenWhenNoAttackOccursYet() {
        Fixture fixture = createCombat();
        long startingStamina = fixture.warrior().getStaminaMilliseconds();

        progressionService.synchronize(
                fixture.combat(), fixture.combat().getLastSynchronizedAt().plusMillis(300));

        assertEquals(CombatStatus.IN_PROGRESS, fixture.combat().getStatus());
        assertTrue(fixture.combat().getEvents().isEmpty());
        assertEquals(startingStamina - 300, fixture.warrior().getStaminaMilliseconds());
        assertEquals(0, fixture.warrior().getSkillPoints(HeroSkill.MELEE).compareTo(BigDecimal.ZERO));
    }

    @Test
    @TestTransaction
    void shouldDrainAndAwardOnceAcrossRepeatedSynchronization() {
        Fixture fixture = createCombat();
        long warriorStamina = fixture.warrior().getStaminaMilliseconds();
        long mageStamina = fixture.mage().getStaminaMilliseconds();
        long archerStamina = fixture.archer().getStaminaMilliseconds();
        Instant synchronizedAt = fixture.combat().getLastSynchronizedAt().plusSeconds(1);

        progressionService.synchronize(fixture.combat(), synchronizedAt);
        assertEquals(CombatStatus.IN_PROGRESS, fixture.combat().getStatus());
        assertEquals(warriorStamina - 1_000, fixture.warrior().getStaminaMilliseconds());
        assertEquals(mageStamina - 1_000, fixture.mage().getStaminaMilliseconds());
        assertEquals(archerStamina - 1_000, fixture.archer().getStaminaMilliseconds());
        assertEquals(0, fixture.warrior().getSkillPoints(HeroSkill.MELEE).compareTo(BigDecimal.ONE));
        assertEquals(0, fixture.archer().getSkillPoints(HeroSkill.DISTANCE).compareTo(BigDecimal.ONE));
        assertEquals(0, fixture.mage().getSkillPoints(HeroSkill.MAGIC).compareTo(new BigDecimal("676.5")));

        progressionService.synchronize(fixture.combat(), synchronizedAt);
        entityManager.flush();
        Fixture reloaded = reload(fixture);

        assertEquals(warriorStamina - 1_000, reloaded.warrior().getStaminaMilliseconds());
        assertEquals(0, reloaded.warrior().getSkillPoints(HeroSkill.MELEE).compareTo(BigDecimal.ONE));
        assertEquals(0, reloaded.mage().getSkillPoints(HeroSkill.MAGIC)
                .compareTo(new BigDecimal("676.5")));
    }

    @Test
    @TestTransaction
    void shouldStopDrainingAnIndividualHeroAfterThatHeroFalls() {
        Fixture fixture = createCombat();
        long mageStamina = fixture.mage().getStaminaMilliseconds();
        long warriorStamina = fixture.warrior().getStaminaMilliseconds();
        QuestCombatant creature = fixture.combat().getCombatants().stream()
                .filter(combatant -> combatant.getTeam() == CombatTeam.CREATURES)
                .findFirst()
                .orElseThrow();
        QuestCombatant mageCombatant = fixture.combat().getCombatants().stream()
                .filter(combatant -> combatant.getHero() != null
                        && combatant.getHero().getId().equals(fixture.mage().getId()))
                .findFirst()
                .orElseThrow();
        QuestCombatant warriorCombatant = fixture.combat().getCombatants().stream()
                .filter(combatant -> combatant.getHero() != null
                        && combatant.getHero().getId().equals(fixture.warrior().getId()))
                .findFirst()
                .orElseThrow();
        CombatEvent fatalHit = new CombatEvent(400, CombatAction.BASIC_ATTACK,
                creature.getId().toString(),
                List.of(new CombatHit(mageCombatant.getId().toString(), 100, false, true)),
                0, 0, 0);
        CombatEvent laterKill = new CombatEvent(600, CombatAction.BASIC_ATTACK,
                warriorCombatant.getId().toString(),
                List.of(new CombatHit(creature.getId().toString(), 120, false, true)),
                0, 0, 0);

        CombatProgressionApplier.apply(
                fixture.combat(), List.of(fatalHit, laterKill), CombatStatus.IN_PROGRESS, 1_000);

        assertEquals(mageStamina - 400, fixture.mage().getStaminaMilliseconds());
        assertEquals(warriorStamina - 1_000, fixture.warrior().getStaminaMilliseconds());
        assertEquals(0, fixture.mage().getExperience());
        assertEquals(150, fixture.warrior().getExperience());
        assertEquals(150, fixture.archer().getExperience());
    }

    @Test
    @TestTransaction
    void shouldProcessTheFullEventStreamBeforeKeepingOnlyRecentUiEvents() {
        Fixture fixture = createCombat();
        entityManager.createNativeQuery("""
                UPDATE quest_combatant
                SET max_health = 1000000,
                    current_health = 1000000,
                    attack_damage = 0
                WHERE combat_id = :combatId
                  AND team = 'CREATURES'
                """)
                .setParameter("combatId", fixture.combat().getId())
                .executeUpdate();
        fixture = reload(fixture);
        long startingStamina = fixture.warrior().getStaminaMilliseconds();

        progressionService.synchronize(
                fixture.combat(), fixture.combat().getLastSynchronizedAt().plusSeconds(60));

        assertEquals(CombatStatus.IN_PROGRESS, fixture.combat().getStatus());
        assertEquals(100, fixture.combat().getEvents().size());
        assertEquals(startingStamina - 60_000, fixture.warrior().getStaminaMilliseconds());
        assertEquals(0, fixture.warrior().getSkillPoints(HeroSkill.MELEE)
                .compareTo(new BigDecimal("46")));
        assertEquals(0, fixture.archer().getSkillPoints(HeroSkill.DISTANCE)
                .compareTo(new BigDecimal("54")));
        assertEquals(0, fixture.mage().getSkillPoints(HeroSkill.MAGIC)
                .compareTo(new BigDecimal("698")));
    }

    @Test
    @TestTransaction
    void shouldStopStaminaDrainAtTheActualTerminalEvent() {
        Fixture fixture = createCombat();
        entityManager.createNativeQuery("""
                UPDATE quest_combatant
                SET current_health = 1
                WHERE combat_id = :combatId
                  AND team = 'CREATURES'
                """)
                .setParameter("combatId", fixture.combat().getId())
                .executeUpdate();
        fixture = reload(fixture);
        long startingStamina = fixture.warrior().getStaminaMilliseconds();
        Instant delayedSync = fixture.combat().getLastSynchronizedAt().plusSeconds(10);

        progressionService.synchronize(fixture.combat(), delayedSync);
        assertEquals(CombatStatus.HERO_VICTORY, fixture.combat().getStatus());
        long terminalAt = fixture.combat().getEvents().stream()
                .mapToLong(event -> event.getOccurredAtMilliseconds())
                .max()
                .orElseThrow();
        assertTrue(terminalAt < 10_000);
        assertEquals(startingStamina - terminalAt, fixture.warrior().getStaminaMilliseconds());
        assertEquals(450, fixture.warrior().getExperience());
        assertEquals(150, fixture.mage().getExperience());
        assertEquals(450, fixture.archer().getExperience());
        entityManager.flush();
        fixture = reload(fixture);
        assertEquals(450, fixture.warrior().getExperience());
        assertEquals(150, fixture.mage().getExperience());

        progressionService.synchronize(fixture.combat(), delayedSync.plusSeconds(5));
        assertEquals(startingStamina - terminalAt, fixture.warrior().getStaminaMilliseconds());
        assertEquals(450, fixture.warrior().getExperience());
    }

    private Fixture createCombat() {
        Agency agency = entityManager.find(Agency.class, AGENCY_ID);
        Party party = new Party(agency, "Progression " + UuidV7.next());
        entityManager.persist(party);
        Hero warrior = createHero(agency, party, HeroClass.WARRIOR);
        Hero mage = createHero(agency, party, HeroClass.MAGE);
        Hero archer = createHero(agency, party, HeroClass.ARCHER);
        mage.addSkillPoints(HeroSkill.MAGIC, new BigDecimal("676"));
        mage.consumeStaminaMilliseconds(HeroProgression.MAX_STAMINA_MILLISECONDS - 41_472_000);
        entityManager.flush();

        UUID questId = UuidV7.next();
        entityManager.createNativeQuery("""
                INSERT INTO quest (
                    id, title, description, status, creature_name,
                    creatures_defeated, creatures_required, minimum_heroes,
                    maximum_heroes, duration_minutes, gold_reward, started_at,
                    expected_completion_at, agency_id, party_id)
                VALUES (
                    :questId, 'Progression fixture', 'Isolated combat test',
                    'IN_PROGRESS', 'Troll', 0, 3, 1, 4, 60, 0,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '60 minutes',
                    :agencyId, :partyId)
                """)
                .setParameter("questId", questId)
                .setParameter("agencyId", agency.getId())
                .setParameter("partyId", party.getId())
                .executeUpdate();
        Quest quest = entityManager.find(Quest.class, questId);
        QuestCombat combat = QuestCombat.start(quest, List.of(warrior, mage, archer));
        entityManager.persist(combat);
        entityManager.flush();
        return new Fixture(combat, warrior, mage, archer);
    }

    private Hero createHero(Agency agency, Party party, HeroClass heroClass) {
        Hero hero = Hero.createRecruitable(
                "Progression " + heroClass, "progression-" + heroClass + "-" + UuidV7.next(), heroClass);
        hero.recruitTo(agency);
        hero.assignToParty(party);
        hero.changeActivity(HeroActivity.ON_QUEST);
        entityManager.persist(hero);
        return hero;
    }

    private Fixture reload(Fixture fixture) {
        UUID combatId = fixture.combat().getId();
        UUID warriorId = fixture.warrior().getId();
        UUID mageId = fixture.mage().getId();
        UUID archerId = fixture.archer().getId();
        entityManager.clear();
        return new Fixture(
                entityManager.find(QuestCombat.class, combatId),
                entityManager.find(Hero.class, warriorId),
                entityManager.find(Hero.class, mageId),
                entityManager.find(Hero.class, archerId));
    }

    private record Fixture(QuestCombat combat, Hero warrior, Hero mage, Hero archer) {
    }
}
