package io.tiagovibeson.heroassociation.application.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.CombatBattleRegistration;
import io.tiagovibeson.heroassociation.domain.CombatProgressionInboxRecord;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroProgression;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.domain.Quest;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CombatInboxApplierTest {

    private static final UUID AGENCY_ID =
            UUID.fromString("019c4c00-0001-7000-8000-000000000001");
    private static final UUID MANAGER_ID =
            UUID.fromString("019c4c00-0000-7000-8000-000000000001");

    @Inject
    CombatInboxService inbox;

    @Inject
    CombatInboxApplier applier;

    @Inject
    ObjectMapper mapper;

    @Inject
    EntityManager entityManager;

    @Test
    @TestTransaction
    void shouldWaitForGapAndAwardExperienceExactlyOnce() throws Exception {
        Party party = party();
        Hero warrior = hero(party, HeroClass.WARRIOR);
        UUID battleId = UuidV7.next();
        UUID combatantId = UuidV7.next();
        applier.register(battleId, party.getId(), Map.of(combatantId, warrior.getId()));

        UUID secondBatchId = UuidV7.next();
        ObjectNode kill = fact(2, 2_000, "CREATURE_KILLED");
        kill.putArray("heroIds").add(warrior.getId().toString());
        kill.put("creatureCombatantId", UuidV7.next().toString());
        kill.put("baseExperience", 100);
        store(secondBatchId, battleId, kill);

        assertFalse(applier.applyNext(battleId));
        assertEquals(0, warrior.getExperience());

        UUID firstBatchId = UuidV7.next();
        ObjectNode stamina = fact(1, 1_000, "STAMINA_ELAPSED");
        stamina.putArray("heroIds").add(warrior.getId().toString());
        stamina.put("elapsedMilliseconds", 3_600_000L);
        store(firstBatchId, battleId, stamina);

        assertTrue(applier.applyNext(battleId));
        assertEquals(HeroProgression.MAX_STAMINA_MILLISECONDS - 3_600_000L,
                warrior.getStaminaMilliseconds());
        assertTrue(applier.applyNext(battleId));
        assertEquals(150, warrior.getExperience());
        assertFalse(applier.applyNext(battleId));
        assertEquals(3, entityManager.find(CombatBattleRegistration.class, battleId).getNextSequence());
        assertNotNull(entityManager.find(CombatProgressionInboxRecord.class, firstBatchId).getAppliedAt());
        assertNotNull(entityManager.find(CombatProgressionInboxRecord.class, secondBatchId).getAppliedAt());

        assertEquals(CombatInboxService.Result.DUPLICATE, store(secondBatchId, battleId, kill));
        assertFalse(applier.applyNext(battleId));
        assertEquals(150, warrior.getExperience());
    }

    @Test
    @TestTransaction
    void shouldApplySkillsAndFinalResourcesForRegisteredHeroes() throws Exception {
        Party party = party();
        Hero warrior = hero(party, HeroClass.WARRIOR);
        Hero mage = hero(party, HeroClass.MAGE);
        UUID battleId = UuidV7.next();
        UUID warriorCombatant = UuidV7.next();
        UUID mageCombatant = UuidV7.next();
        applier.register(battleId, party.getId(), Map.of(
                warriorCombatant, warrior.getId(), mageCombatant, mage.getId()));

        ObjectNode warriorAction = fact(1, 1_000, "HERO_ACTION");
        warriorAction.put("heroId", warrior.getId().toString());
        warriorAction.put("action", "BASIC_ATTACK");
        warriorAction.put("manaSpent", 0);

        ObjectNode mageAction = fact(2, 1_500, "HERO_ACTION");
        mageAction.put("heroId", mage.getId().toString());
        mageAction.put("action", "FIRE_BALL");
        mageAction.put("manaSpent", 20);

        ObjectNode kill = fact(3, 2_000, "CREATURE_KILLED");
        kill.putArray("heroIds").add(warrior.getId().toString()).add(mage.getId().toString());
        kill.put("creatureCombatantId", UuidV7.next().toString());
        kill.put("baseExperience", 100);

        ObjectNode completion = fact(4, 2_000, "BATTLE_COMPLETED");
        ObjectNode snapshot = completion.putObject("finalSnapshot");
        snapshot.put("status", "HERO_VICTORY");
        var finalHeroes = snapshot.putArray("heroes");
        finalHeroes.addObject()
                .put("id", warriorCombatant.toString())
                .put("currentHealth", 200)
                .put("currentMana", 40);
        finalHeroes.addObject()
                .put("id", mageCombatant.toString())
                .put("currentHealth", 80)
                .put("currentMana", 450);

        store(UuidV7.next(), battleId, warriorAction, mageAction, kill, completion);
        assertTrue(applier.applyNext(battleId));
        assertEquals(150, warrior.getExperience());
        assertEquals(150, mage.getExperience());
        assertEquals(0, warrior.getSkillPoints(HeroSkill.MELEE).compareTo(BigDecimal.ONE));
        assertEquals(0, mage.getSkillPoints(HeroSkill.MAGIC).compareTo(BigDecimal.ONE));
        assertEquals(200, warrior.getCurrentHealth());
        assertEquals(40, warrior.getCurrentMana());
        assertEquals(80, mage.getCurrentHealth());
        assertEquals(450, mage.getCurrentMana());
        assertNotNull(entityManager.find(CombatBattleRegistration.class, battleId).getCompletedAt());
        assertFalse(applier.applyNext(battleId));
        warrior.changeActivity(HeroActivity.RESTING);
        applier.register(battleId, party.getId(), Map.of(
                warriorCombatant, warrior.getId(), mageCombatant, mage.getId()));
        assertEquals(5, entityManager.find(CombatBattleRegistration.class, battleId).getNextSequence());

    }

    @Test
    @TestTransaction
    void shouldAcceptCreatureKillWithNoLivingHeroRecipients() throws Exception {
        Party party = party();
        Hero warrior = hero(party, HeroClass.WARRIOR);
        UUID battleId = UuidV7.next();
        applier.register(battleId, party.getId(), Map.of(UuidV7.next(), warrior.getId()));

        ObjectNode kill = fact(1, 1_000, "CREATURE_KILLED");
        kill.putArray("heroIds");
        kill.put("creatureCombatantId", UuidV7.next().toString());
        kill.put("baseExperience", 100);
        store(UuidV7.next(), battleId, kill);

        assertTrue(applier.applyNext(battleId));
        assertEquals(0, warrior.getExperience());
    }

    @Test
    void shouldRejectRegistrationForAQuestStillOwnedByCore() {
        UUID livePartyId = UUID.fromString("019c4c00-0002-7000-8000-000000000001");
        UUID liveHeroId = UUID.fromString("019c4c00-0010-7000-8000-000000000001");
        assertThrows(IllegalArgumentException.class, () ->
                applier.register(UuidV7.next(), livePartyId, Map.of(UuidV7.next(), liveHeroId)));
    }

    @Test
    @TestTransaction
    void shouldStopApplyingIfCoreLaterOwnsThePartyQuest() throws Exception {
        Party party = party();
        Hero warrior = hero(party, HeroClass.WARRIOR);
        UUID battleId = UuidV7.next();
        applier.register(battleId, party.getId(), Map.of(UuidV7.next(), warrior.getId()));

        ObjectNode stamina = fact(1, 1_000, "STAMINA_ELAPSED");
        stamina.putArray("heroIds").add(warrior.getId().toString());
        stamina.put("elapsedMilliseconds", 1_000);
        store(UuidV7.next(), battleId, stamina);

        Quest available = entityManager.find(Quest.class,
                UUID.fromString("019c4c00-0004-7000-8000-000000000001"));
        available.startWith(party);
        assertThrows(IllegalStateException.class, () -> applier.applyNext(battleId));
    }

    private Party party() {
        Agency agency = entityManager.find(Agency.class, AGENCY_ID);
        Manager manager = entityManager.find(Manager.class, MANAGER_ID);
        Party party = new Party(agency, manager, "Inbox " + UuidV7.next());
        entityManager.persist(party);
        entityManager.flush();
        return party;
    }

    private Hero hero(Party party, HeroClass heroClass) {
        String suffix = UuidV7.next().toString();
        Hero hero = Hero.createPersonal(
                "Inbox " + heroClass + " " + suffix,
                "Inbox" + suffix, heroClass, party.getOwnerManager());
        hero.assignToParty(party);
        entityManager.persist(hero);
        entityManager.flush();
        hero.changeActivity(HeroActivity.ON_QUEST);
        return hero;
    }

    private ObjectNode fact(long sequence, long occurredAt, String type) {
        ObjectNode fact = mapper.createObjectNode();
        fact.put("sequence", sequence);
        fact.put("occurredAtMilliseconds", occurredAt);
        fact.put("type", type);
        return fact;
    }

    private CombatInboxService.Result store(
            UUID batchId, UUID battleId, ObjectNode... facts) throws Exception {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("schemaVersion", 1);
        envelope.put("batchId", batchId.toString());
        envelope.put("battleId", battleId.toString());
        envelope.put("firstSequence", facts[0].path("sequence").longValue());
        envelope.put("lastSequence", facts[facts.length - 1].path("sequence").longValue());
        envelope.put("createdAt", Instant.EPOCH.toString());
        var array = envelope.putArray("facts");
        for (ObjectNode fact : facts) {
            array.add(fact);
        }
        return inbox.receive(mapper.writeValueAsBytes(envelope), batchId.toString(), "application/json");
    }
}
