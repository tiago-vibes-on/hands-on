package io.tiagovibeson.heroassociation.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.time.Instant;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.tiagovibeson.heroassociation.application.assets.*;
import io.tiagovibeson.heroassociation.application.expedition.ExpeditionAdmissionService;
import io.tiagovibeson.heroassociation.domain.*;
import io.tiagovibeson.heroassociation.testsupport.AssetsStubResource;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.*;

@QuarkusTest @TestSecurity(user="local-seed-soren")
class HeroBorrowingPaymentServiceTest {
    static final UUID AGENCY=UUID.fromString("019c4c00-0001-7000-8000-000000000001"), MANAGER=UUID.fromString("019c4c00-0000-7000-8000-000000000003"), ATTACK=UUID.fromString("019c4c00-0020-7000-8000-000000000001");
    @Inject EntityManager em;
    @Inject QuestStartService quests;
    @Inject RuneLoadoutService runes;
    @Inject AssetWorkflowTransactions transactions;
    @Inject AssetWorkflows workflows;
    @Inject AssetsClient assets;
    @Inject PartyManagementService parties;
    @Inject ExpeditionAdmissionService admission;
    private Fixture fixture;
    @BeforeEach void create() {
        AssetsStubResource.reset();
        fixture=QuarkusTransaction.requiringNew().call(()->{
            Agency agency=em.find(Agency.class,AGENCY);Manager manager=em.find(Manager.class,MANAGER);
            Party party=new Party(agency,manager,"Asset workflow "+UuidV7.next());em.persist(party);
            Hero hero=Hero.createRecruitable("Workflow Hero","Workflow"+UuidV7.next(),HeroClass.WARRIOR);hero.recruitTo(agency);hero.setBorrowingFeeGold(25);hero.assignToParty(party);em.persist(hero);em.flush();
            UUID quest=UuidV7.next();em.createNativeQuery("INSERT INTO quest (id,title,description,status,creature_name,creatures_defeated,creatures_required,minimum_heroes,maximum_heroes,duration_minutes,gold_reward,agency_id) VALUES (:id,'Asset test','Temporary quest','AVAILABLE','Forest Wolf',0,1,1,2,30,0,:agency)").setParameter("id",quest).setParameter("agency",AGENCY).executeUpdate();
            return new Fixture(hero.getId(),party.getId(),quest);
        });
    }
    @AfterEach void cleanup() {
        AssetsStubResource.unavailable=false;
        QuarkusTransaction.requiringNew().run(()->{
            var combat=em.createQuery("select c from QuestCombat c where c.quest.id=:id",QuestCombat.class).setParameter("id",fixture.quest()).getResultStream().findFirst().orElse(null);if(combat!=null)em.remove(combat);em.flush();
            em.createQuery("delete from AssetWorkflow w where w.questId=:quest or w.heroId=:hero").setParameter("quest",fixture.quest()).setParameter("hero",fixture.hero()).executeUpdate();
            em.remove(em.find(Quest.class,fixture.quest()));em.flush();em.remove(em.find(Hero.class,fixture.hero()));em.flush();em.remove(em.find(Party.class,fixture.party()));
        });AssetsStubResource.reset();
    }
    private String status(UUID key) {return transactions.view(key,MANAGER).status();}
    private HeroActivity activity(){return QuarkusTransaction.requiringNew().call(()->em.find(Hero.class,fixture.hero()).getActivity());}
    private UUID fence(){return QuarkusTransaction.requiringNew().call(()->em.find(Hero.class,fixture.hero()).getPendingAssetOperation());}
    @Test void questPaymentAndExactRetryStartOneCombat() {
        UUID key=UuidV7.next();assertEquals(200,quests.startQuest(AGENCY,fixture.quest(),fixture.party(),25,key).getStatus());
        assertEquals(200,quests.startQuest(AGENCY,fixture.quest(),fixture.party(),25,key).getStatus());assertEquals(0,AssetsStubResource.gold(MANAGER));assertEquals(1,AssetsStubResource.appliedCommands);assertEquals(HeroActivity.ON_QUEST,activity());
        QuarkusTransaction.requiringNew().run(()->assertEquals(1L,em.createQuery("select count(c) from QuestCombat c where c.quest.id=:id",Long.class).setParameter("id",fixture.quest()).getSingleResult()));
    }
    @Test void lostPaymentResponseKeepsBothFencesAndRecoversWithoutAnotherCharge() {
        UUID key=UuidV7.next();AssetsStubResource.loseNextResponse=true;
        assertEquals(202,quests.startQuest(AGENCY,fixture.quest(),fixture.party(),25,key).getStatus());assertEquals("PENDING",status(key));assertEquals(key,fence());assertEquals(HeroActivity.TRAINING,activity());assertEquals(0,AssetsStubResource.gold(MANAGER));
        assertThrows(WebApplicationException.class,()->parties.removeHero(AGENCY,fixture.party(),fixture.hero()));
        assertThrows(WebApplicationException.class,()->transactions.rune(UuidV7.next(),AGENCY,fixture.hero(),0,ATTACK,RuneInventoryOwnerType.AGENCY,true));
        workflows.recover(key);assertEquals("APPLIED",status(key));assertNull(fence());assertEquals(1,AssetsStubResource.appliedCommands);
    }
    @Test void outageBeforePaymentKeepsHeroesAvailableOnlyToTheirAcceptedWorkflow() {
        UUID key=transactions.quest(UuidV7.next(),AGENCY,fixture.quest(),fixture.party(),25);AssetsStubResource.unavailable=true;workflows.recover(key);
        assertEquals("PENDING",status(key));assertEquals(25,AssetsStubResource.gold(MANAGER));assertEquals(key,fence());
        AssetsStubResource.unavailable=false;workflows.recover(key);assertEquals("APPLIED",status(key));assertEquals(0,AssetsStubResource.gold(MANAGER));
    }
    @Test void rejectedPaymentClearsFencesAndDoesNotStartCombat() {
        AssetsStubResource.gold(MANAGER,0);UUID key=transactions.quest(UuidV7.next(),AGENCY,fixture.quest(),fixture.party(),25);workflows.recover(key);
        assertEquals("REJECTED",status(key));assertNull(fence());assertEquals(HeroActivity.TRAINING,activity());
        QuarkusTransaction.requiringNew().run(()->{assertNull(em.find(Party.class,fixture.party()).getPendingAssetOperation());assertEquals(QuestStatus.AVAILABLE,em.find(Quest.class,fixture.quest()).getStatus());});
    }
    @Test void feeMismatchIsRejectedBeforeStagingOrCallingAssets() {
        assertThrows(io.tiagovibeson.heroassociation.application.exception.BorrowingFeeRejectedException.class,()->transactions.quest(UuidV7.next(),AGENCY,fixture.quest(),fixture.party(),0));assertEquals(0,AssetsStubResource.appliedCommands);assertNull(fence());
    }
    @Test void freeAgencyHeroStartsWithoutACharge() {
        QuarkusTransaction.requiringNew().run(()->em.find(Hero.class,fixture.hero()).setBorrowingFeeGold(0));UUID key=UuidV7.next();quests.startQuest(AGENCY,fixture.quest(),fixture.party(),0,key);assertEquals(25,AssetsStubResource.gold(MANAGER));assertEquals(HeroActivity.ON_QUEST,activity());
    }
    @Test void resourcesRecoverBeforeBeingPinnedToTheNewBattle() {
        QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("update hero set current_health=10,current_mana=0,stamina_milliseconds=100000000,last_resource_synchronized_at=CURRENT_TIMESTAMP-INTERVAL '60 seconds' where id=:id").setParameter("id",fixture.hero()).executeUpdate());
        quests.startQuest(AGENCY,fixture.quest(),fixture.party(),25,UuidV7.next());
        QuarkusTransaction.requiringNew().run(()->{Hero hero=em.find(Hero.class,fixture.hero());assertTrue(hero.getCurrentHealth()>10);var snapshot=em.find(Quest.class,fixture.quest()).getCombat().getCombatants().stream().filter(c->c.getHero()!=null).findFirst().orElseThrow();assertEquals(hero.getCurrentHealth(),snapshot.getCurrentHealth());assertEquals(hero.getStaminaMilliseconds(),snapshot.getStartingStaminaMilliseconds());});
    }
    @Test void aKeyIsBoundToActorKindAndImmutableRequest() {
        UUID key=transactions.quest(UuidV7.next(),AGENCY,fixture.quest(),fixture.party(),25);
        assertThrows(WebApplicationException.class,()->transactions.quest(key,AGENCY,fixture.quest(),fixture.party(),26));
        assertThrows(WebApplicationException.class,()->transactions.rune(key,AGENCY,fixture.hero(),0,ATTACK,RuneInventoryOwnerType.AGENCY,true));assertEquals(key,fence());
        QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("update asset_workflow set managerid='019c4c00-0000-7000-8000-000000000001' where id=:id").setParameter("id",key).executeUpdate());
        assertThrows(WebApplicationException.class,()->transactions.quest(key,AGENCY,fixture.quest(),fixture.party(),25));
    }
    @Test void lostEquipmentResponseRecoversOnceAndBlocksOtherChanges() {
        UUID key=transactions.rune(UuidV7.next(),AGENCY,fixture.hero(),0,ATTACK,RuneInventoryOwnerType.AGENCY,true);AssetsStubResource.loseNextResponse=true;workflows.recover(key);
        assertEquals("PENDING",status(key));assertEquals(key,fence());
        QuarkusTransaction.requiringNew().run(()->assertThrows(WebApplicationException.class,()->em.find(Hero.class,fixture.hero()).changeActivity(HeroActivity.RESTING)));
        assertThrows(WebApplicationException.class,()->transactions.quest(UuidV7.next(),AGENCY,fixture.quest(),fixture.party(),25));
        workflows.recover(key);assertEquals("APPLIED",status(key));assertEquals(1,AssetsStubResource.appliedCommands);assertNull(fence());
        QuarkusTransaction.requiringNew().run(()->{Hero hero=em.find(Hero.class,fixture.hero());assets.decorate(List.of(hero), assets.snapshot(List.of(), List.of(hero.getId())).heroes());assertEquals(ATTACK,hero.getRuneSlots().getFirst().getRune().getId());});
    }
    @Test void staleClaimCannotFinalizeAfterItsReplacement() {
        UUID key=transactions.rune(UuidV7.next(),AGENCY,fixture.hero(),0,ATTACK,RuneInventoryOwnerType.AGENCY,true);var old=transactions.claim(key);
        QuarkusTransaction.requiringNew().run(()->em.find(AssetWorkflow.class,key).claimUntil=Instant.now().minusSeconds(1));var current=transactions.claim(key);
        try {var receipt=assets.execute(new com.fasterxml.jackson.databind.ObjectMapper().readTree(current.commandJson()));transactions.finish(old,receipt);assertEquals("PENDING",status(key));transactions.finish(current,receipt);assertEquals("APPLIED",status(key));}
        catch(java.io.IOException impossible){throw new AssertionError(impossible);}
    }
    @Test void contradictoryReceiptIsQuarantinedWithEligibilityStillFenced() {
        UUID key=transactions.rune(UuidV7.next(),AGENCY,fixture.hero(),0,ATTACK,RuneInventoryOwnerType.AGENCY,true);AssetsStubResource.corruptNextReceipt=true;workflows.recover(key);assertEquals("CONFLICT",status(key));assertEquals(key,fence());workflows.recover(key);assertEquals("CONFLICT",status(key));assertEquals(1,AssetsStubResource.appliedCommands);
    }
    @Test void pendingEquipmentPreventsAddingTheHeroToAnotherParty() {
        UUID key=transactions.rune(UuidV7.next(),AGENCY,fixture.hero(),0,ATTACK,RuneInventoryOwnerType.AGENCY,true);
        assertThrows(WebApplicationException.class,()->parties.removeHero(AGENCY,fixture.party(),fixture.hero()));assertEquals(key,fence());
    }
    private record Fixture(UUID hero,UUID party,UUID quest){}
}
