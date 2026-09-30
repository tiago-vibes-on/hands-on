package io.tiagovibeson.heroassociation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.BorrowingFeeRejectedException;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.Quest;
import io.tiagovibeson.heroassociation.domain.QuestCombatant;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "local-seed-soren")
class HeroBorrowingPaymentServiceTest {

    private static final UUID AGENCY_ID = UUID.fromString("019c4c00-0001-7000-8000-000000000001");
    private static final UUID SOREN_ID = UUID.fromString("019c4c00-0000-7000-8000-000000000003");
    private static final UUID OAKSHIELD_ID = UUID.fromString("019c4c00-0010-7000-8000-000000000004");
    private static final UUID EMBERVEIL_ID = UUID.fromString("019c4c00-0010-7000-8000-000000000005");
    private static final UUID HAWKEYE_ID = UUID.fromString("019c4c00-0010-7000-8000-000000000006");

    @Inject
    EntityManager entityManager;

    @Inject
    PartyManagementService partyManagementService;

    @Inject
    QuestStartService questStartService;

    @Inject
    ManagerRepository managerRepository;

    @Inject
    AgencyRepository agencyRepository;

    @Test
    @TestTransaction
    void shouldChargeTheExactBorrowingFeeOnlyWhenQuestStarts() {
        UUID questId = createQuest();
        UUID partyId = createPartyWith(EMBERVEIL_ID, "Paid borrowing");

        assertEquals(25, managerRepository.findById(SOREN_ID).getGold());
        long startingAgencyGold = agencyRepository.findById(AGENCY_ID).getGold();
        AgencyStateResponse state = questStartService.startQuest(AGENCY_ID, questId, partyId, 25);

        assertEquals(0, managerRepository.findById(SOREN_ID).getGold());
        assertEquals(startingAgencyGold + 25, state.agency().gold());
        assertEquals("IN_PROGRESS", state.quests().stream()
                .filter(quest -> quest.id().equals(questId)).findFirst().orElseThrow().status());
    }

    @Test
    @TestTransaction
    void shouldRejectAStaleQuoteWithoutCharging() {
        UUID questId = createQuest();
        UUID partyId = createPartyWith(EMBERVEIL_ID, "Stale borrowing");

        BorrowingFeeRejectedException exception = assertThrows(BorrowingFeeRejectedException.class,
                () -> questStartService.startQuest(AGENCY_ID, questId, partyId, 0));

        assertEquals("Borrowing fee changed: expected 0 gold, current total is 25 gold.", exception.getMessage());
        assertEquals(25, managerRepository.findById(SOREN_ID).getGold());
    }

    @Test
    @TestTransaction
    void shouldRejectInsufficientPersonalGoldWithoutCharging() {
        UUID questId = createQuest();
        UUID partyId = createPartyWith(HAWKEYE_ID, "Unaffordable borrowing");

        BorrowingFeeRejectedException exception = assertThrows(BorrowingFeeRejectedException.class,
                () -> questStartService.startQuest(AGENCY_ID, questId, partyId, 100));

        assertEquals("Insufficient personal gold: need 100 gold, have 25 gold.", exception.getMessage());
        assertEquals(25, managerRepository.findById(SOREN_ID).getGold());
    }

    @Test
    @TestTransaction
    void shouldStartWithoutChargingForAFreeAgencyHero() {
        UUID questId = createQuest();
        UUID partyId = createPartyWith(OAKSHIELD_ID, "Free borrowing");

        long startingAgencyGold = agencyRepository.findById(AGENCY_ID).getGold();
        AgencyStateResponse state = questStartService.startQuest(AGENCY_ID, questId, partyId, 0);

        assertEquals(25, managerRepository.findById(SOREN_ID).getGold());
        assertEquals(startingAgencyGold, state.agency().gold());
    }

    @Test
    @TestTransaction
    void shouldRecoverHeroBeforePinningBattleStartResources() {
        UUID questId = createQuest();
        UUID partyId = createPartyWith(OAKSHIELD_ID, "Recovered borrowing");
        entityManager.createNativeQuery("""
                UPDATE hero
                SET current_health = 10,
                    current_mana = 0,
                    stamina_milliseconds = 100000000,
                    last_resource_synchronized_at = CURRENT_TIMESTAMP - INTERVAL '60 seconds'
                WHERE id = :heroId
                """)
                .setParameter("heroId", OAKSHIELD_ID)
                .executeUpdate();
        entityManager.clear();

        questStartService.startQuest(AGENCY_ID, questId, partyId, 0);

        Hero hero = entityManager.find(Hero.class, OAKSHIELD_ID);
        QuestCombatant snapshot = entityManager.find(Quest.class, questId).getCombat().getCombatants().stream()
                .filter(combatant -> combatant.getHero() != null)
                .findFirst()
                .orElseThrow();
        assertTrue(hero.getCurrentHealth() > 10);
        assertTrue(hero.getCurrentMana() > 0);
        assertTrue(hero.getStaminaMilliseconds() > 100000000);
        assertEquals(hero.getCurrentHealth(), snapshot.getCurrentHealth());
        assertEquals(hero.getCurrentMana(), snapshot.getCurrentMana());
        assertEquals(hero.getStaminaMilliseconds(), snapshot.getStartingStaminaMilliseconds());
    }

    private UUID createQuest() {
        UUID questId = UuidV7.next();
        entityManager.createNativeQuery("""
                INSERT INTO quest (
                    id, title, description, status, creature_name, creatures_defeated,
                    creatures_required, minimum_heroes, maximum_heroes, duration_minutes,
                    gold_reward, agency_id
                ) VALUES (
                    :id, 'Borrowing test', 'Temporary test quest', 'AVAILABLE', 'Forest Wolf', 0,
                    1, 1, 2, 30, 0, :agencyId
                )
                """)
                .setParameter("id", questId)
                .setParameter("agencyId", AGENCY_ID)
                .executeUpdate();
        return questId;
    }

    private UUID createPartyWith(UUID heroId, String name) {
        UUID partyId = partyManagementService.createParty(AGENCY_ID, name).parties().stream()
                .filter(party -> party.name().equals(name))
                .findFirst().orElseThrow().id();
        partyManagementService.addHero(AGENCY_ID, partyId, heroId);
        return partyId;
    }
}
