package io.tiagovibeson.heroassociation.application.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @Inject io.tiagovibeson.heroassociation.application.assets.AssetsClient assets;
    @Inject io.tiagovibeson.heroassociation.application.assets.AssetWorkflows workflows;
    private Fixture createdFixture;
    @org.junit.jupiter.api.BeforeEach void resetAssets() { io.tiagovibeson.heroassociation.testsupport.AssetsStubResource.reset(); }
    @org.junit.jupiter.api.AfterEach void cleanupFixture() {
        io.tiagovibeson.heroassociation.testsupport.AssetsStubResource.unavailable = false;
        if (createdFixture != null) io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("delete from AssetWorkflow w where w.expeditionId in (select r.id from ExpeditionReservation r where r.partyId=:party)").setParameter("party",createdFixture.party().getId()).executeUpdate();
            em.createQuery("delete from ExpeditionReservation r where r.partyId=:party").setParameter("party",createdFixture.party().getId()).executeUpdate();
            em.remove(em.find(Hero.class,createdFixture.hero().getId()));em.flush();em.remove(em.find(Party.class,createdFixture.party().getId()));
        });
        io.tiagovibeson.heroassociation.testsupport.AssetsStubResource.reset();
    }
    private Hero currentHero(Fixture fixture) { return io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> em.find(Hero.class,fixture.hero().getId())); }
    private ExpeditionReservation reservation(UUID id) { return io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> em.find(ExpeditionReservation.class,id)); }
    private long gold() { return io.tiagovibeson.heroassociation.testsupport.AssetsStubResource.gold(MANAGER_ID); }


    @Test
    void appliesAggregateOnceAcrossDuplicateDelivery() throws Exception {
        Fixture fixture = fixture();
        UUID expeditionId = UuidV7.next();
        ExpeditionBaseline baseline = admission.reserve(expeditionId, MANAGER_ID, AGENCY_ID, fixture.party().getId());
        assertEquals(baseline, admission.reserve(expeditionId, MANAGER_ID, AGENCY_ID, fixture.party().getId()));
        assertEquals(HeroActivity.ON_EXPEDITION, currentHero(fixture).getActivity());
        long goldBefore = gold();
        int itemBefore = itemQuantity();
        int runeBefore = runeQuantity();

        byte[] body = payload(expeditionId, fixture, 100, "1.500000", 2, 1);
        assertEquals(ExpeditionSettlementService.Result.APPLIED,
                settlement.apply(body, expeditionId.toString(), "application/json"));
        assertEquals(100, currentHero(fixture).getExperience());
        assertEquals(0, currentHero(fixture).getSkillPoints(HeroSkill.MELEE).compareTo(new BigDecimal("1.5")));
        assertEquals(baseline.heroes().getFirst().staminaMilliseconds() - 1_000,
                currentHero(fixture).getStaminaMilliseconds());
        assertEquals(250, currentHero(fixture).getCurrentHealth());
        assertEquals(HeroActivity.RESTING, currentHero(fixture).getActivity());
        assertEquals(goldBefore + 7, gold());
        assertEquals(itemBefore + 2, itemQuantity());
        assertEquals(runeBefore + 1, runeQuantity());
        assertNotNull(reservation(expeditionId).getAppliedAt());

        assertEquals(ExpeditionSettlementService.Result.DUPLICATE,
                settlement.apply(body, expeditionId.toString(), "application/json"));
        assertEquals(goldBefore + 7, gold());
        assertEquals(itemBefore + 2, itemQuantity());
        assertEquals(runeBefore + 1, runeQuantity());
        assertThrows(IllegalStateException.class,
                () -> admission.reserve(expeditionId, MANAGER_ID, AGENCY_ID, fixture.party().getId()));
        assertThrows(IllegalArgumentException.class,
                () -> settlement.apply(payload(expeditionId, fixture, 101, "1.500000", 2, 1),
                        expeditionId.toString(), "application/json"));
    }

    @Test
    void rejectsAnUnreservedAggregateBeforeChangingHero() throws Exception {
        Fixture fixture = fixture();
        UUID expeditionId = UuidV7.next();
        assertThrows(IllegalStateException.class,
                () -> settlement.apply(payload(expeditionId, fixture, 100, "1.500000", 0, 0),
                        expeditionId.toString(), "application/json"));
        assertEquals(0, currentHero(fixture).getExperience());
    }

    @Test
    void refusesSettlementWhenTheReservedHeroWasChangedInCore() throws Exception {
        Fixture fixture = fixture();
        UUID expeditionId = UuidV7.next();
        admission.reserve(expeditionId, MANAGER_ID, AGENCY_ID, fixture.party().getId());
        long goldBefore = gold();
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> em.find(Hero.class,fixture.hero().getId()).addExperience(1));
        assertThrows(IllegalStateException.class,
                () -> settlement.apply(payload(expeditionId, fixture, 100, "1.500000", 0, 0),
                        expeditionId.toString(), "application/json"));
        assertEquals(goldBefore, gold());
        assertEquals(1, currentHero(fixture).getExperience());
        assertEquals(null, reservation(expeditionId).getAppliedAt());
    }

    @Test
    void releasesAProvenAbsentRunWithoutReusingItsReservationId() {
        Fixture fixture = fixture();
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> em.find(Hero.class,fixture.hero().getId()).changeActivity(HeroActivity.RESTING));
        UUID expeditionId = UuidV7.next();
        ExpeditionBaseline baseline = admission.reserve(expeditionId, MANAGER_ID, AGENCY_ID, fixture.party().getId());
        assertEquals(HeroActivity.RESTING, baseline.heroes().getFirst().previousActivity());
        assertEquals(currentHero(fixture).getName(), baseline.heroes().getFirst().name());
        assertEquals(2.0, baseline.heroes().getFirst().criticalDamageMultiplier());
        assertTrue(admission.orphanCandidates(Instant.now().plusSeconds(1), 100).stream()
                .anyMatch(candidate -> candidate.expeditionId().equals(expeditionId)));
        assertTrue(admission.releaseProvenAbsent(expeditionId, MANAGER_ID));
        assertFalse(admission.orphanCandidates(Instant.now().plusSeconds(1), 100).stream()
                .anyMatch(candidate -> candidate.expeditionId().equals(expeditionId)));
        assertFalse(admission.releaseProvenAbsent(expeditionId, MANAGER_ID));
        assertEquals(HeroActivity.RESTING, currentHero(fixture).getActivity());
        assertNotNull(reservation(expeditionId).getReleasedAt());
        assertThrows(IllegalStateException.class,
                () -> admission.reserve(expeditionId, MANAGER_ID, AGENCY_ID, fixture.party().getId()));
    }

    @Test
    void refusesToReleaseAChangedReservedHero() {
        Fixture fixture = fixture();
        UUID expeditionId = UuidV7.next();
        admission.reserve(expeditionId, MANAGER_ID, AGENCY_ID, fixture.party().getId());
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> em.find(Hero.class,fixture.hero().getId()).addExperience(1));
        assertThrows(IllegalStateException.class,
                () -> admission.releaseProvenAbsent(expeditionId, MANAGER_ID));
        assertEquals(HeroActivity.ON_EXPEDITION, currentHero(fixture).getActivity());
        assertEquals(null, reservation(expeditionId).getReleasedAt());
    }

    private Fixture fixture() {
        createdFixture = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
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
        });
        return createdFixture;
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
        finalHero.put("staminaMilliseconds", currentHero(fixture).getStaminaMilliseconds() - 1_000);
        root.put("gold", 7);
        ObjectNode carriedItems = root.putObject("items");
        if (items > 0) carriedItems.put(ITEM_ID.toString(), items);
        ObjectNode carriedRunes = root.putObject("runes");
        if (runes > 0) carriedRunes.put(RUNE_ID.toString(), runes);
        return mapper.writeValueAsBytes(root);
    }

    private int itemQuantity() {
        return assets.snapshot(java.util.List.of(new io.tiagovibeson.heroassociation.application.assets.AssetsClient.OwnerRequest("MANAGER",MANAGER_ID)),java.util.List.of()).owner(MANAGER_ID).items().stream().filter(row -> row.item().id().equals(ITEM_ID)).mapToInt(row -> row.quantity()).sum();
    }
    private int runeQuantity() {
        return assets.snapshot(java.util.List.of(new io.tiagovibeson.heroassociation.application.assets.AssetsClient.OwnerRequest("MANAGER",MANAGER_ID)),java.util.List.of()).owner(MANAGER_ID).runes().stream().filter(row -> row.rune().id().equals(RUNE_ID)).mapToInt(row -> row.quantity()).sum();
    }
    @Test void settlementWaitsForBothAssetCreditAndHeroProgressBeforeAcknowledgement() throws Exception {
        Fixture fixture=fixture();UUID expeditionId=UuidV7.next();admission.reserve(expeditionId,MANAGER_ID,AGENCY_ID,fixture.party().getId());
        byte[] body=payload(expeditionId,fixture,100,"1.500000",2,1);
        io.tiagovibeson.heroassociation.testsupport.AssetsStubResource.loseNextResponse=true;
        assertThrows(jakarta.ws.rs.WebApplicationException.class,()->settlement.apply(body,expeditionId.toString(),"application/json"));
        assertEquals(7,gold());assertEquals(0,currentHero(fixture).getExperience());assertEquals(HeroActivity.ON_EXPEDITION,currentHero(fixture).getActivity());assertNull(reservation(expeditionId).getAppliedAt());
        workflows.recover(reservation(expeditionId).getAssetsSettlementKey());
        assertEquals(100,currentHero(fixture).getExperience());assertNotNull(reservation(expeditionId).getAppliedAt());
        assertEquals(ExpeditionSettlementService.Result.DUPLICATE,settlement.apply(body,expeditionId.toString(),"application/json"));assertEquals(7,gold());
    }
    @Test void admissionCanRecoverALostPinnedLoadoutResponseUsingTheSameReservation() {
        Fixture fixture=fixture();UUID expeditionId=UuidV7.next();io.tiagovibeson.heroassociation.testsupport.AssetsStubResource.loseNextResponse=true;
        assertThrows(jakarta.ws.rs.WebApplicationException.class,()->admission.reserve(expeditionId,MANAGER_ID,AGENCY_ID,fixture.party().getId()));
        assertEquals(HeroActivity.ON_EXPEDITION,currentHero(fixture).getActivity());assertFalse(reservation(expeditionId).isAssetsSnapshotConfirmed());
        admission.reserve(expeditionId,MANAGER_ID,AGENCY_ID,fixture.party().getId());assertTrue(reservation(expeditionId).isAssetsSnapshotConfirmed());assertEquals(1,io.tiagovibeson.heroassociation.testsupport.AssetsStubResource.appliedCommands);
    }
    private record Fixture(Manager manager, Party party, Hero hero) { }
}
