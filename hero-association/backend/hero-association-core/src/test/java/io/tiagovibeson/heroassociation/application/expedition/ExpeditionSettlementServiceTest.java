package io.tiagovibeson.heroassociation.application.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.ExpeditionReservation;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.domain.ManagerItem;
import io.tiagovibeson.heroassociation.domain.ManagerRune;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@QuarkusTest
class ExpeditionSettlementServiceTest {

    private static final UUID AGENCY_ID = UUID.fromString("019c4c00-0001-7000-8000-000000000001");
    private static final UUID MANAGER_ID = UUID.fromString("019c4c00-0000-7000-8000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("019c4c00-0070-7000-8000-000000000001");
    private static final UUID RUNE_ID = UUID.fromString("019c4c00-0020-7000-8000-000000000001");

    @Inject ExpeditionAdmissionService admission;
    @Inject ExpeditionSettlementService settlement;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;

    @Test
    @TestTransaction
    void appliesAggregateOnceAcrossDuplicateDelivery() throws Exception {
        Fixture fixture = fixture();
        UUID expeditionId = UuidV7.next();
        ExpeditionBaseline baseline = admission.reserve(expeditionId, MANAGER_ID, AGENCY_ID, fixture.party().getId());
        assertEquals(baseline, admission.reserve(expeditionId, MANAGER_ID, AGENCY_ID, fixture.party().getId()));
        assertEquals(HeroActivity.ON_EXPEDITION, fixture.hero().getActivity());
        long goldBefore = fixture.manager().getGold();
        int itemBefore = itemQuantity();
        int runeBefore = runeQuantity();

        byte[] body = payload(expeditionId, fixture, 100, "1.500000", 2, 1);
        assertEquals(ExpeditionSettlementService.Result.APPLIED,
                settlement.apply(body, expeditionId.toString(), "application/json"));
        assertEquals(100, fixture.hero().getExperience());
        assertEquals(0, fixture.hero().getSkillPoints(HeroSkill.MELEE).compareTo(new BigDecimal("1.5")));
        assertEquals(baseline.heroes().getFirst().staminaMilliseconds() - 1_000,
                fixture.hero().getStaminaMilliseconds());
        assertEquals(250, fixture.hero().getCurrentHealth());
        assertEquals(HeroActivity.RESTING, fixture.hero().getActivity());
        assertEquals(goldBefore + 7, fixture.manager().getGold());
        assertEquals(itemBefore + 2, itemQuantity());
        assertEquals(runeBefore + 1, runeQuantity());
        assertNotNull(em.find(ExpeditionReservation.class, expeditionId).getAppliedAt());

        assertEquals(ExpeditionSettlementService.Result.DUPLICATE,
                settlement.apply(body, expeditionId.toString(), "application/json"));
        assertEquals(goldBefore + 7, fixture.manager().getGold());
        assertEquals(itemBefore + 2, itemQuantity());
        assertEquals(runeBefore + 1, runeQuantity());
        assertThrows(IllegalStateException.class,
                () -> admission.reserve(expeditionId, MANAGER_ID, AGENCY_ID, fixture.party().getId()));
        assertThrows(IllegalArgumentException.class,
                () -> settlement.apply(payload(expeditionId, fixture, 101, "1.500000", 2, 1),
                        expeditionId.toString(), "application/json"));
    }

    @Test
    @TestTransaction
    void rejectsAnUnreservedAggregateBeforeChangingHero() throws Exception {
        Fixture fixture = fixture();
        UUID expeditionId = UuidV7.next();
        assertThrows(IllegalStateException.class,
                () -> settlement.apply(payload(expeditionId, fixture, 100, "1.500000", 0, 0),
                        expeditionId.toString(), "application/json"));
        assertEquals(0, fixture.hero().getExperience());
    }

    @Test
    @TestTransaction
    void refusesSettlementWhenTheReservedHeroWasChangedInCore() throws Exception {
        Fixture fixture = fixture();
        UUID expeditionId = UuidV7.next();
        admission.reserve(expeditionId, MANAGER_ID, AGENCY_ID, fixture.party().getId());
        long goldBefore = fixture.manager().getGold();
        fixture.hero().addExperience(1);
        assertThrows(IllegalStateException.class,
                () -> settlement.apply(payload(expeditionId, fixture, 100, "1.500000", 0, 0),
                        expeditionId.toString(), "application/json"));
        assertEquals(goldBefore, fixture.manager().getGold());
        assertEquals(1, fixture.hero().getExperience());
        assertEquals(null, em.find(ExpeditionReservation.class, expeditionId).getAppliedAt());
    }

    @Test
    @TestTransaction
    void releasesAProvenAbsentRunWithoutReusingItsReservationId() {
        Fixture fixture = fixture();
        fixture.hero().changeActivity(HeroActivity.RESTING);
        UUID expeditionId = UuidV7.next();
        ExpeditionBaseline baseline = admission.reserve(expeditionId, MANAGER_ID, AGENCY_ID, fixture.party().getId());
        assertEquals(HeroActivity.RESTING, baseline.heroes().getFirst().previousActivity());
        assertEquals(fixture.hero().getName(), baseline.heroes().getFirst().name());
        assertEquals(2.0, baseline.heroes().getFirst().criticalDamageMultiplier());
        assertTrue(admission.orphanCandidates(Instant.now().plusSeconds(1), 100).stream()
                .anyMatch(candidate -> candidate.expeditionId().equals(expeditionId)));
        assertTrue(admission.releaseProvenAbsent(expeditionId, MANAGER_ID));
        assertFalse(admission.orphanCandidates(Instant.now().plusSeconds(1), 100).stream()
                .anyMatch(candidate -> candidate.expeditionId().equals(expeditionId)));
        assertFalse(admission.releaseProvenAbsent(expeditionId, MANAGER_ID));
        assertEquals(HeroActivity.RESTING, fixture.hero().getActivity());
        assertNotNull(em.find(ExpeditionReservation.class, expeditionId).getReleasedAt());
        assertThrows(IllegalStateException.class,
                () -> admission.reserve(expeditionId, MANAGER_ID, AGENCY_ID, fixture.party().getId()));
    }

    @Test
    @TestTransaction
    void refusesToReleaseAChangedReservedHero() {
        Fixture fixture = fixture();
        UUID expeditionId = UuidV7.next();
        admission.reserve(expeditionId, MANAGER_ID, AGENCY_ID, fixture.party().getId());
        fixture.hero().addExperience(1);
        assertThrows(IllegalStateException.class,
                () -> admission.releaseProvenAbsent(expeditionId, MANAGER_ID));
        assertEquals(HeroActivity.ON_EXPEDITION, fixture.hero().getActivity());
        assertEquals(null, em.find(ExpeditionReservation.class, expeditionId).getReleasedAt());
    }

    private Fixture fixture() {
        Agency agency = em.find(Agency.class, AGENCY_ID);
        Manager manager = em.find(Manager.class, MANAGER_ID);
        Party party = new Party(agency, manager, "Settlement " + UuidV7.next());
        em.persist(party);
        Hero hero = Hero.createPersonal("Settlement Warrior", "Settle" + UuidV7.next(),
                HeroClass.WARRIOR, manager);
        hero.assignToParty(party);
        em.persist(hero);
        em.flush();
        return new Fixture(manager, party, hero);
    }

    private byte[] payload(UUID expeditionId, Fixture fixture, long experience,
                           String melee, int items, int runes) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("expeditionId", expeditionId.toString());
        root.put("ownerManagerId", MANAGER_ID.toString());
        root.put("agencyId", AGENCY_ID.toString());
        root.put("partyId", fixture.party().getId().toString());
        ObjectNode finalHero = root.putArray("heroes").addObject();
        finalHero.put("heroId", fixture.hero().getId().toString());
        finalHero.put("heroClass", "WARRIOR");
        finalHero.put("experience", experience);
        ObjectNode points = finalHero.putObject("skillPoints");
        for (HeroSkill skill : HeroSkill.values()) {
            points.put(skill.name(), new BigDecimal(skill == HeroSkill.MELEE ? melee : "0.000000"));
        }
        finalHero.put("health", 250);
        finalHero.put("mana", 40);
        finalHero.put("staminaMilliseconds", fixture.hero().getStaminaMilliseconds() - 1_000);
        root.put("gold", 7);
        ObjectNode carriedItems = root.putObject("items");
        if (items > 0) carriedItems.put(ITEM_ID.toString(), items);
        ObjectNode carriedRunes = root.putObject("runes");
        if (runes > 0) carriedRunes.put(RUNE_ID.toString(), runes);
        return mapper.writeValueAsBytes(root);
    }

    private int itemQuantity() {
        return em.createQuery("select i from ManagerItem i where i.manager.id = :manager and i.item.id = :item",
                        ManagerItem.class)
                .setParameter("manager", MANAGER_ID).setParameter("item", ITEM_ID).getResultStream()
                .mapToInt(ManagerItem::getQuantity).sum();
    }

    private int runeQuantity() {
        return em.createQuery("select r from ManagerRune r where r.manager.id = :manager and r.rune.id = :rune",
                        ManagerRune.class)
                .setParameter("manager", MANAGER_ID).setParameter("rune", RUNE_ID).getResultStream()
                .mapToInt(ManagerRune::getQuantity).sum();
    }

    private record Fixture(Manager manager, Party party, Hero hero) { }
}
