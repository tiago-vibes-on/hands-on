package io.tiagovibeson.heroassociation.combat.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.tiagovibeson.heroassociation.combat.application.BattleAdvanceService;
import io.tiagovibeson.heroassociation.combat.application.BattleWorker;
import io.tiagovibeson.heroassociation.combat.application.BattleOutboxDelivery;
import io.tiagovibeson.heroassociation.combat.application.BattleOutboxWorker;
import io.tiagovibeson.heroassociation.combat.application.RecordingProgressionBatchTransport;
import io.tiagovibeson.heroassociation.combat.application.ProgressionBatchMessage;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.tiagovibeson.heroassociation.combat.domain.BattleRecord;
import io.tiagovibeson.heroassociation.combat.domain.BattleEventRecord;
import io.tiagovibeson.heroassociation.combat.domain.BattleProgressionBatchRecord;
import io.tiagovibeson.heroassociation.combat.domain.ProgressionFact;
import io.tiagovibeson.heroassociation.combat.domain.ProgressionFactType;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattle;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattleSnapshot;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BattleResourceTest {

    @Inject
    EntityManager entityManager;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    BattleAdvanceService battleAdvanceService;

    @Inject
    BattleWorker battleWorker;


    @Inject
    BattleOutboxDelivery outboxDelivery;

    @Inject
    BattleOutboxWorker outboxWorker;

    @Inject
    RecordingProgressionBatchTransport recordingTransport;
    @Test
    void shouldRejectUnauthenticatedStart() {
        given()
                .contentType("application/json")
                .body(request())
                .when().post("/internal/v1/battles")
                .then().statusCode(401);
        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 1_000))
                .when().post("/internal/v1/battles/{battleId}/advance", uuidV7())
                .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "viewer", roles = "viewer")
    void shouldRejectCallerWithoutStartRole() {
        given()
                .contentType("application/json")
                .body(request())
                .when().post("/internal/v1/battles")
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "core", roles = "combat:start")
    void shouldCreateAndReplayTheSameStartWithoutCreatingAnotherBattle() throws Exception {
        Map<String, Object> command = request();

        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(201)
                .body("battleId", equalTo(command.get("battleId")))
                .body("status", equalTo("PREPARED"))
                .body("replayed", equalTo(false));

        UUID battleId = UUID.fromString((String) command.get("battleId"));
        BattleRecord storedRecord = entityManager.find(BattleRecord.class, battleId);
        CombatBattleSnapshot storedState = objectMapper.readValue(
                storedRecord.getSnapshotJson(), CombatBattleSnapshot.class);
        assertEquals("PREPARED", storedRecord.getStatus());
        assertEquals(0, storedState.currentTimeMilliseconds());
        StartBattleRequest parsedRequest = objectMapper.convertValue(command, StartBattleRequest.class);
        assertEquals(objectMapper.readValue(storedRecord.getRequestJson(), StartBattleRequest.class), parsedRequest);

        assertEquals(1, CombatBattle.restore(storedState).getHeroes().size());
        assertEquals(2_000, storedState.creatures().getFirst().currentHealth());

        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(200)
                .body("battleId", equalTo(command.get("battleId")))
                .body("replayed", equalTo(true));
        assertEquals(storedRecord.getSnapshotJson(),
                entityManager.find(BattleRecord.class, battleId).getSnapshotJson());
    }

    @Test
    @TestSecurity(user = "core", roles = "combat:start")
    void shouldRejectDifferentPayloadForAnExistingBattleId() {
        Map<String, Object> command = request();

        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(201);

        Map<String, Object> different = new LinkedHashMap<>(command);
        different.put("runId", uuidV7().toString());
        given().contentType("application/json").body(different)
                .when().post("/internal/v1/battles")
                .then().statusCode(409);
    }

    @Test
    @TestSecurity(user = "core", roles = "combat:start")
    void shouldRejectEmptyFormationAndNonUuidV7Ids() {
        Map<String, Object> command = request();
        command.put("battleId", UUID.randomUUID().toString());
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(400);

        command.put("battleId", uuidV7().toString());
        Map<String, Object> emptyFormation = new LinkedHashMap<>(openingSnapshot(
                uuidV7().toString(), uuidV7().toString()));
        emptyFormation.put("heroes", List.of());
        command.put("initialSnapshot", emptyFormation);
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "core", roles = "combat:start")
    void shouldRejectInvalidStatsAndDuplicateCombatantIds() {
        Map<String, Object> command = request();
        String duplicateId = uuidV7().toString();
        command.put("initialSnapshot", openingSnapshot(duplicateId, duplicateId));
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(400);

        Map<String, Object> invalidHero = new LinkedHashMap<>(
                combatant(uuidV7().toString(), "Warrior", "HEROES", 300, 50, 22, 480));
        invalidHero.put("maxHealth", -1);
        Map<String, Object> invalidState = new LinkedHashMap<>(
                openingSnapshot(uuidV7().toString(), uuidV7().toString()));
        invalidState.put("heroes", List.of(invalidHero));
        command.put("initialSnapshot", invalidState);
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(400);

        Map<String, Object> missingSpellTimer = new LinkedHashMap<>(
                combatant(uuidV7().toString(), "Mage", "HEROES", 100, 500, 10, 480));
        missingSpellTimer.put("spells", List.of("FIRE_BALL"));
        Map<String, Object> invalidCooldownState = new LinkedHashMap<>(
                openingSnapshot(uuidV7().toString(), uuidV7().toString()));
        invalidCooldownState.put("heroes", List.of(missingSpellTimer));
        command.put("initialSnapshot", invalidCooldownState);
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "core", roles = "combat:start")
    void shouldRejectUnsupportedRulesetAndAdvancedStartTime() {
        Map<String, Object> command = request();
        command.put("rulesetVersion", "unknown");
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(400);

        command.put("rulesetVersion", "core-v1");
        Map<String, Object> advancedState = new LinkedHashMap<>(
                openingSnapshot(uuidV7().toString(), uuidV7().toString()));
        advancedState.put("currentTimeMilliseconds", 5_000);
        command.put("initialSnapshot", advancedState);
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(400);
    }
    @Test
    @TestSecurity(user = "core", roles = "combat:start")
    void shouldAcceptLowLevelMageWithUnscheduledSpells() {
        Map<String, Object> command = request();
        Map<String, Object> heroInput = firstHeroInput(command);
        heroInput.put("heroClass", "MAGE");

        String heroCombatantId = (String) heroInput.get("combatantId");
        String creatureCombatantId = (String) firstCreatureInput(command).get("combatantId");
        Map<String, Object> mage = new LinkedHashMap<>(
                combatant(heroCombatantId, "Mage", "HEROES", 100, 500, 32, 480));
        mage.put("basicAttackManaCost", 20);
        mage.put("attackIntervalMilliseconds", 1_700);
        mage.put("healthRecoveryPerSecond", 2);
        mage.put("manaRecoveryPerSecond", 10);
        mage.put("spells", List.of("FIRE_BALL", "LIGHTNING_RAIL"));

        Map<String, Object> snapshot = new LinkedHashMap<>(
                openingSnapshot(heroCombatantId, creatureCombatantId));
        snapshot.put("heroes", List.of(mage));
        command.put("initialSnapshot", snapshot);

        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "core", roles = "combat:start")
    void shouldRejectMissingOrMismatchedProgressionInputs() {
        Map<String, Object> command = request();
        command.remove("heroInputs");
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(400);

        command = request();
        firstHeroInput(command).put("combatantId", uuidV7().toString());
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(400);

        command = request();
        firstHeroInput(command).put("startingStaminaMilliseconds", 172_800_001);
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(400);

        command = request();
        Map<String, Object> creature = new LinkedHashMap<>(firstCreatureInput(command));
        creature.put("baseExperience", -1);
        command.put("creatureInputs", List.of(creature));
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "core", roles = "combat:start")
    @SuppressWarnings("unchecked")
    void shouldPinRuneSlotsAndRequireMatchingCriticalStats() throws Exception {
        Map<String, Object> command = request();
        Map<String, Object> heroInput = firstHeroInput(command);
        heroInput.put("runeSlots", List.of(
                Map.of("runeId", uuidV7().toString(), "slotIndex", 0,
                        "code", "CRITICAL_CHANCE", "effect", "CRITICAL_CHANCE", "effectValue", 0.01),
                Map.of("runeId", uuidV7().toString(), "slotIndex", 1,
                        "code", "CRITICAL_DAMAGE", "effect", "CRITICAL_DAMAGE", "effectValue", 0.1)));

        Map<String, Object> snapshot = new LinkedHashMap<>((Map<String, Object>) command.get("initialSnapshot"));
        Map<String, Object> hero = new LinkedHashMap<>(
                (Map<String, Object>) ((List<?>) snapshot.get("heroes")).getFirst());
        hero.put("criticalChance", 0.01);
        hero.put("criticalDamageMultiplier", 2.1);
        snapshot.put("heroes", List.of(hero));
        command.put("initialSnapshot", snapshot);

        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(201);
        BattleRecord record = entityManager.find(
                BattleRecord.class, UUID.fromString((String) command.get("battleId")));
        StartBattleRequest stored = objectMapper.readValue(record.getRequestJson(), StartBattleRequest.class);
        assertEquals(2, stored.heroInputs().getFirst().runeSlots().size());

        hero.put("criticalChance", 0.0);
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(400);

        command = request();
        firstHeroInput(command).put("runeSlots", List.of(
                Map.of("runeId", uuidV7().toString(), "slotIndex", 0,
                        "code", "ARMOR", "effect", "ARMOR", "effectValue", 1.0),
                Map.of("runeId", uuidV7().toString(), "slotIndex", 0,
                        "code", "MANA", "effect", "MANA", "effectValue", 1.0)));
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(400);
    }
    @Test
    @TestSecurity(user = "engine", roles = { "combat:start", "combat:advance" })
    void shouldJournalAdvancesAndReplayWithoutDuplicateFacts() throws Exception {
        Map<String, Object> command = request();
        String battleId = (String) command.get("battleId");
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles")
                .then().statusCode(201);

        var first = given().contentType("application/json")
                .body(Map.of("targetTimeMilliseconds", 1_000))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId)
                .then().statusCode(200).extract().response();
        assertEquals("IN_PROGRESS", first.jsonPath().getString("status"));
        assertEquals(1_000L, first.jsonPath().getLong("currentTimeMilliseconds"));
        long firstEventSequence = first.jsonPath().getLong("lastEventSequence");
        long firstFactSequence = first.jsonPath().getLong("lastFactSequence");
        assertTrue(firstEventSequence > 0);
        assertTrue(firstFactSequence > 0);

        UUID id = UUID.fromString(battleId);
        BattleRecord record = entityManager.find(BattleRecord.class, id);
        CombatBattleSnapshot state = objectMapper.readValue(record.getSnapshotJson(), CombatBattleSnapshot.class);
        assertEquals(1_000L, state.currentTimeMilliseconds());
        var events = entityManager.createQuery(
                "from BattleEventRecord e where e.battle.id = :id order by e.sequenceNumber",
                BattleEventRecord.class).setParameter("id", id).getResultList();
        assertEquals(firstEventSequence, events.size());
        for (int index = 0; index < events.size(); index++) {
            assertEquals(index + 1L, events.get(index).getSequenceNumber());
        }
        var batches = entityManager.createQuery(
                "from BattleProgressionBatchRecord b where b.battle.id = :id order by b.firstSequence",
                BattleProgressionBatchRecord.class).setParameter("id", id).getResultList();
        assertEquals(1, batches.size());
        assertEquals(1L, batches.getFirst().getFirstSequence());
        assertEquals(firstFactSequence, batches.getFirst().getLastSequence());
        assertEquals(null, batches.getFirst().getPublishedAt());
        List<ProgressionFact> facts = objectMapper.readValue(
                batches.getFirst().getFactsJson(), new TypeReference<>() {});
        assertTrue(facts.stream().anyMatch(fact -> fact.type() == ProgressionFactType.HERO_ACTION));
        long staminaElapsed = facts.stream()
                .filter(fact -> fact.type() == ProgressionFactType.STAMINA_ELAPSED)
                .mapToLong(ProgressionFact::elapsedMilliseconds).sum();
        assertEquals(1_000L, staminaElapsed);
        UUID heroId = UUID.fromString((String) firstHeroInput(command).get("heroId"));
        assertTrue(facts.stream().filter(fact -> fact.type() == ProgressionFactType.STAMINA_ELAPSED)
                .allMatch(fact -> fact.heroIds().equals(List.of(heroId))));

        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 1_000))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId)
                .then().statusCode(200)
                .body("replayed", equalTo(true))
                .body("lastEventSequence", equalTo((int) firstEventSequence))
                .body("lastFactSequence", equalTo((int) firstFactSequence));
        assertEquals(1, entityManager.createQuery(
                "from BattleProgressionBatchRecord b where b.battle.id = :id",
                BattleProgressionBatchRecord.class).setParameter("id", id).getResultList().size());

        var second = given().contentType("application/json")
                .body(Map.of("targetTimeMilliseconds", 2_000))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId)
                .then().statusCode(200).extract().response();
        assertTrue(second.jsonPath().getLong("lastEventSequence") > firstEventSequence);
        assertTrue(second.jsonPath().getLong("lastFactSequence") > firstFactSequence);
        var allBatches = entityManager.createQuery(
                "from BattleProgressionBatchRecord b where b.battle.id = :id order by b.firstSequence",
                BattleProgressionBatchRecord.class).setParameter("id", id).getResultList();
        assertEquals(2, allBatches.size());
        assertEquals(firstFactSequence + 1, allBatches.get(1).getFirstSequence());
    }

    @Test
    @TestSecurity(user = "engine", roles = { "combat:start", "combat:advance" })
    @SuppressWarnings("unchecked")
    void shouldPersistTerminalFactAndStopAdvancing() throws Exception {
        Map<String, Object> command = request();
        Map<String, Object> snapshot = new LinkedHashMap<>((Map<String, Object>) command.get("initialSnapshot"));
        Map<String, Object> creature = new LinkedHashMap<>(
                (Map<String, Object>) ((List<?>) snapshot.get("creatures")).getFirst());
        creature.put("maxHealth", 1);
        creature.put("currentHealth", 1);
        snapshot.put("creatures", List.of(creature));
        command.put("initialSnapshot", snapshot);
        String battleId = (String) command.get("battleId");

        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles").then().statusCode(201);
        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 1_000))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId)
                .then().statusCode(200).body("status", equalTo("HERO_VICTORY"));

        UUID id = UUID.fromString(battleId);
        var batches = entityManager.createQuery(
                "from BattleProgressionBatchRecord b where b.battle.id = :id",
                BattleProgressionBatchRecord.class).setParameter("id", id).getResultList();
        assertEquals(1, batches.size());
        List<ProgressionFact> facts = objectMapper.readValue(
                batches.getFirst().getFactsJson(), new TypeReference<>() {});
        assertTrue(facts.stream().anyMatch(fact -> fact.type() == ProgressionFactType.CREATURE_KILLED
                && fact.baseExperience() == 100));
        ProgressionFact terminal = facts.getLast();
        assertEquals(ProgressionFactType.BATTLE_COMPLETED, terminal.type());
        assertEquals("HERO_VICTORY", terminal.finalSnapshot().status().name());
        assertEquals(480L, terminal.occurredAtMilliseconds());
        assertEquals(480L, facts.stream()
                .filter(fact -> fact.type() == ProgressionFactType.STAMINA_ELAPSED)
                .mapToLong(ProgressionFact::elapsedMilliseconds).sum());

        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 1_000))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId)
                .then().statusCode(200).body("replayed", equalTo(true));
        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 2_000))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId)
                .then().statusCode(409);
        assertFalse(battleAdvanceService.advanceDue(id,
                entityManager.find(BattleRecord.class, id).getLastAdvancedAt().plusSeconds(10)));
        assertEquals(1, batches.size());
    }

    @Test
    @TestSecurity(user = "core", roles = "combat:start")
    void shouldRequireSeparateAdvanceRole() {
        Map<String, Object> command = request();
        String battleId = (String) command.get("battleId");
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles").then().statusCode(201);
        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 1_000))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId)
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "engine", roles = { "combat:start", "combat:advance" })
    void shouldRejectOversizedOrBackwardAdvance() {
        Map<String, Object> command = request();
        String battleId = (String) command.get("battleId");
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles").then().statusCode(201);
        given().contentType("application/json").body(Map.of())
                .when().post("/internal/v1/battles/{battleId}/advance", battleId)
                .then().statusCode(400);
        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 10_001))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId)
                .then().statusCode(400);
        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 1_000))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId)
                .then().statusCode(200);
        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 500))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId)
                .then().statusCode(409);
        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 1_000))
                .when().post("/internal/v1/battles/{battleId}/advance", uuidV7())
                .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "engine", roles = { "combat:start", "combat:advance" })
    @SuppressWarnings("unchecked")
    void shouldRecordHeroFallWithoutChargingStaminaAfterTheFall() throws Exception {
        Map<String, Object> command = request();
        Map<String, Object> snapshot = new LinkedHashMap<>((Map<String, Object>) command.get("initialSnapshot"));
        Map<String, Object> hero = new LinkedHashMap<>(
                (Map<String, Object>) ((List<?>) snapshot.get("heroes")).getFirst());
        hero.put("currentHealth", 1);
        snapshot.put("heroes", List.of(hero));
        command.put("initialSnapshot", snapshot);
        String battleId = (String) command.get("battleId");

        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles").then().statusCode(201);
        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 1_000))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId)
                .then().statusCode(200).body("status", equalTo("CREATURE_VICTORY"));

        var batch = entityManager.createQuery(
                "from BattleProgressionBatchRecord b where b.battle.id = :id",
                BattleProgressionBatchRecord.class)
                .setParameter("id", UUID.fromString(battleId)).getSingleResult();
        List<ProgressionFact> facts = objectMapper.readValue(
                batch.getFactsJson(), new TypeReference<>() {});
        assertTrue(facts.stream().anyMatch(fact -> fact.type() == ProgressionFactType.HERO_FELL
                && fact.heroId().equals(UUID.fromString((String) firstHeroInput(command).get("heroId")))));
        assertEquals(760L, facts.stream()
                .filter(fact -> fact.type() == ProgressionFactType.STAMINA_ELAPSED)
                .mapToLong(ProgressionFact::elapsedMilliseconds).sum());
        assertEquals(ProgressionFactType.BATTLE_COMPLETED, facts.getLast().type());
    }
    @Test
    @TestSecurity(user = "core", roles = "combat:start")
    void shouldAdvanceDueBattlesInBoundedPersistedSteps() throws Exception {
        Map<String, Object> command = request();
        UUID battleId = UUID.fromString((String) command.get("battleId"));
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles").then().statusCode(201);

        BattleRecord prepared = entityManager.find(BattleRecord.class, battleId);
        java.time.Instant anchor = prepared.getLastAdvancedAt();
        assertEquals(prepared.getCreatedAt(), anchor);
        assertTrue(battleWorker.advanceDueAt(anchor.plusMillis(1_000)) >= 1);
        assertEquals(1_000L, snapshotTime(battleId));
        assertFalse(battleAdvanceService.advanceDue(battleId, anchor.plusMillis(1_000)));

        java.time.Instant twentyFiveSecondsLater = anchor.plusMillis(25_000);
        assertTrue(battleAdvanceService.advanceDue(battleId, twentyFiveSecondsLater));
        assertEquals(11_000L, snapshotTime(battleId));
        assertTrue(battleAdvanceService.advanceDue(battleId, twentyFiveSecondsLater));
        assertEquals(21_000L, snapshotTime(battleId));
        assertTrue(battleAdvanceService.advanceDue(battleId, twentyFiveSecondsLater));
        assertEquals(25_000L, snapshotTime(battleId));
        assertFalse(battleAdvanceService.advanceDue(battleId, twentyFiveSecondsLater));
        assertEquals(twentyFiveSecondsLater,
                entityManager.find(BattleRecord.class, battleId).getLastAdvancedAt());
        assertEquals(4, entityManager.createQuery(
                "from BattleProgressionBatchRecord b where b.battle.id = :id",
                BattleProgressionBatchRecord.class)
                .setParameter("id", battleId).getResultList().size());
    }

    @Test
    @TestSecurity(user = "core", roles = "combat:start")
    void shouldNotDoubleAdvanceWhenTwoWorkersRace() throws Exception {
        Map<String, Object> command = request();
        UUID battleId = UUID.fromString((String) command.get("battleId"));
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles").then().statusCode(201);
        java.time.Instant dueAt = entityManager.find(BattleRecord.class, battleId)
                .getLastAdvancedAt().plusMillis(1_000);

        var gate = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> advance = () -> {
                gate.await();
                return battleAdvanceService.advanceDue(battleId, dueAt);
            };
            var first = executor.submit(advance);
            var second = executor.submit(advance);
            gate.countDown();
            int advanced = (first.get(20, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, advanced);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1_000L, snapshotTime(battleId));
        assertEquals(1, entityManager.createQuery(
                "from BattleProgressionBatchRecord b where b.battle.id = :id",
                BattleProgressionBatchRecord.class)
                .setParameter("id", battleId).getResultList().size());
    }

    @Test
    @TestSecurity(user = "engine", roles = { "combat:start", "combat:advance" })
    void shouldPublishBatchesInSequenceAndMarkThemOnlyAfterDelivery() {
        while (outboxDelivery.publishOne()) {
            // Clear earlier sandbox batches; this test starts with an empty outbox.
        }
        recordingTransport.reset();
        Map<String, Object> command = request();
        String battleId = (String) command.get("battleId");
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles").then().statusCode(201);
        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 1_000))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId).then().statusCode(200);
        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 2_000))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId).then().statusCode(200);

        assertEquals(2, outboxWorker.publishPending());
        assertFalse(outboxDelivery.publishOne());
        List<ProgressionBatchMessage> messages = recordingTransport.messages();
        assertEquals(2, messages.size());
        assertEquals(UUID.fromString(battleId), messages.getFirst().battleId());
        assertEquals(1, messages.getFirst().schemaVersion());
        assertEquals(messages.getFirst().lastSequence() + 1, messages.get(1).firstSequence());
        assertEquals(messages.getFirst().firstSequence(), messages.getFirst().facts().getFirst().sequence());
        assertEquals(messages.get(1).lastSequence(), messages.get(1).facts().getLast().sequence());

        entityManager.clear();
        var batches = entityManager.createQuery(
                "from BattleProgressionBatchRecord b where b.battle.id = :id order by b.firstSequence",
                BattleProgressionBatchRecord.class)
                .setParameter("id", UUID.fromString(battleId)).getResultList();
        assertEquals(messages.getFirst().batchId(), batches.getFirst().getId());
        assertEquals(messages.get(1).batchId(), batches.get(1).getId());
        assertTrue(batches.stream().allMatch(batch -> batch.getPublishedAt() != null));
    }

    @Test
    @TestSecurity(user = "engine", roles = { "combat:start", "combat:advance" })
    void shouldRetryUnconfirmedBatchWithTheSameId() {
        while (outboxDelivery.publishOne()) {
            // Clear earlier sandbox batches.
        }
        recordingTransport.reset();
        Map<String, Object> command = request();
        String battleId = (String) command.get("battleId");
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles").then().statusCode(201);
        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 1_000))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId).then().statusCode(200);
        UUID id = UUID.fromString(battleId);
        var batch = entityManager.createQuery(
                "from BattleProgressionBatchRecord b where b.battle.id = :id",
                BattleProgressionBatchRecord.class).setParameter("id", id).getSingleResult();
        UUID batchId = batch.getId();

        recordingTransport.failNext();
        assertThrows(IllegalStateException.class, () -> outboxDelivery.publishOne());
        entityManager.clear();
        assertEquals(null, entityManager.find(BattleProgressionBatchRecord.class, batchId).getPublishedAt());
        recordingTransport.reset();
        assertTrue(outboxDelivery.publishOne());
        assertEquals(batchId, recordingTransport.messages().getFirst().batchId());
        assertFalse(outboxDelivery.publishOne());
        entityManager.clear();
        assertTrue(entityManager.find(BattleProgressionBatchRecord.class, batchId).getPublishedAt() != null);
    }

    @Test
    @TestSecurity(user = "engine", roles = { "combat:start", "combat:advance" })
    void shouldSkipBatchClaimedByAnotherPublisher() throws Exception {
        while (outboxDelivery.publishOne()) {
            // Clear earlier sandbox batches.
        }
        recordingTransport.reset();
        Map<String, Object> command = request();
        String battleId = (String) command.get("battleId");
        given().contentType("application/json").body(command)
                .when().post("/internal/v1/battles").then().statusCode(201);
        given().contentType("application/json").body(Map.of("targetTimeMilliseconds", 1_000))
                .when().post("/internal/v1/battles/{battleId}/advance", battleId).then().statusCode(200);
        UUID batchId = entityManager.createQuery(
                "from BattleProgressionBatchRecord b where b.battle.id = :id",
                BattleProgressionBatchRecord.class)
                .setParameter("id", UUID.fromString(battleId)).getSingleResult().getId();
        entityManager.clear();

        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var holder = executor.submit(() -> io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            entityManager.find(BattleProgressionBatchRecord.class, batchId,
                    jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            locked.countDown();
            try {
                if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release the outbox row.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }));
        try {
            assertTrue(locked.await(10, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(outboxDelivery.publishOne());
            assertTrue(recordingTransport.messages().isEmpty());
        } finally {
            release.countDown();
            try {
                holder.get(10, java.util.concurrent.TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
        }
        assertTrue(outboxDelivery.publishOne());
        assertEquals(batchId, recordingTransport.messages().getFirst().batchId());
    }

    private long snapshotTime(UUID battleId) throws Exception {
        entityManager.clear();
        BattleRecord record = entityManager.find(BattleRecord.class, battleId);
        return objectMapper.readValue(record.getSnapshotJson(), CombatBattleSnapshot.class)
                .currentTimeMilliseconds();
    }


    private Map<String, Object> request() {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("battleId", uuidV7().toString());
        command.put("source", "QUEST_CORE");
        command.put("runId", uuidV7().toString());
        command.put("partyId", uuidV7().toString());
        command.put("ownerManagerId", uuidV7().toString());
        command.put("rulesetVersion", "core-v1");
        String heroCombatantId = uuidV7().toString();
        String creatureCombatantId = uuidV7().toString();
        command.put("initialSnapshot", openingSnapshot(heroCombatantId, creatureCombatantId));
        command.put("heroInputs", List.of(heroInput(heroCombatantId)));
        command.put("creatureInputs", List.of(creatureInput(creatureCombatantId)));
        return command;
    }

    private Map<String, Object> openingSnapshot(String heroId, String creatureId) {
        return Map.of(
                "currentTimeMilliseconds", 0,
                "nextRecoveryAt", 1_000,
                "status", "IN_PROGRESS",
                "heroes", List.of(combatant(heroId, "Warrior", "HEROES", 300, 50, 22, 480)),
                "creatures", List.of(combatant(creatureId, "Troll", "CREATURES", 2_000, 100, 1, 760)));
    }
    private Map<String, Object> heroInput(String combatantId) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("combatantId", combatantId);
        input.put("heroId", uuidV7().toString());
        input.put("heroClass", "WARRIOR");
        input.put("level", 1);
        input.put("meleeLevel", 1);
        input.put("distanceLevel", 1);
        input.put("shieldLevel", 1);
        input.put("magicLevel", 1);
        input.put("startingStaminaMilliseconds", 172_800_000);
        input.put("runeSlots", List.of());
        return input;
    }

    private Map<String, Object> creatureInput(String combatantId) {
        return Map.of(
                "combatantId", combatantId,
                "definitionId", uuidV7().toString(),
                "definitionVersion", 1,
                "baseExperience", 100);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstHeroInput(Map<String, Object> command) {
        return (Map<String, Object>) ((List<?>) command.get("heroInputs")).getFirst();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstCreatureInput(Map<String, Object> command) {
        return (Map<String, Object>) ((List<?>) command.get("creatureInputs")).getFirst();
    }



    private Map<String, Object> combatant(
            String id, String name, String team, int health, int mana, int damage, long nextAttackAt) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("name", name);
        value.put("team", team);
        value.put("maxHealth", health);
        value.put("maxMana", mana);
        value.put("currentHealth", health);
        value.put("currentMana", mana);
        value.put("attackDamage", damage);
        value.put("basicAttackManaCost", 0);
        value.put("attackIntervalMilliseconds", team.equals("HEROES") ? 1_300 : 1_850);
        value.put("healthRecoveryPerSecond", team.equals("HEROES") ? 10 : 0);
        value.put("manaRecoveryPerSecond", team.equals("HEROES") ? 2 : 0);
        value.put("magicLevel", team.equals("HEROES") ? 1 : 0);
        value.put("criticalChance", 0.0);
        value.put("criticalDamageMultiplier", 2.0);
        value.put("spells", List.of());
        value.put("nextBasicAttackAt", nextAttackAt);
        value.put("nextSpellCastAt", Map.of());
        return value;
    }

    private UUID uuidV7() {
        long timestamp = System.currentTimeMillis();
        long mostSignificantBits = (timestamp << 16) | 0x7000L | ThreadLocalRandom.current().nextInt(1 << 12);
        long leastSignificantBits = (ThreadLocalRandom.current().nextLong() & 0x3fff_ffff_ffff_ffffL)
                | 0x8000_0000_0000_0000L;
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
