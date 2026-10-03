package io.tiagovibeson.heroassociation.quest;

import java.util.*;
import java.util.concurrent.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.tiagovibeson.heroassociation.contract.QuestContract.*;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ClientErrorException;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;

@QuarkusTest @QuarkusTestResource(QuestRemoteTestResource.class)
class QuestFlowTest {
    static final UUID MANAGER = QuestRemoteTestResource.MANAGER;
    static final UUID AGENCY = UUID.fromString("019c4c00-0001-7000-8000-000000000001");
    static final UUID FIELD = UUID.fromString("019c4c00-0006-7000-8000-000000000001");
    static final UUID DUNGEON = UUID.fromString("019c4c00-0006-7000-8000-000000000002");
    static final UUID TROLL = UUID.fromString("019c4c00-0005-7000-8000-000000000001");
    static final UUID KILLS = UUID.fromString("019c4c00-0003-7000-8000-000000000001");
    static final UUID BOSS = UUID.fromString("019c4c00-0004-7000-8000-000000000002");
    static final UUID CLEAR = UUID.fromString("019c4c00-0004-7000-8000-000000000003");
    @Inject QuestTransactions transactions;
    @Inject QuestReturns returns;
    @Inject QuestAssetsClient assets;
    @Inject EntityManager em;
    @Inject com.fasterxml.jackson.databind.ObjectMapper mapper;

    @BeforeEach void reset() {
        QuestRemoteTestResource.reset();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("TRUNCATE quest_command, quest_admission, quest_assignment, quest_manager").executeUpdate());
    }
    @Test void incompleteProgressBanksOnceAndCompletesAcrossReturns() {
        Assignment assignment = transactions.accept(UuidV7.next(), MANAGER, KILLS, true);
        Pin first = pin(FIELD);
        var partial = new Progress(first, 0).advance(Map.of(TROLL, 1), false, false);
        ReturnRequest firstReturn = request(first, partial);
        assertEquals("APPLIED", returns.apply(firstReturn).status());
        assertEquals("APPLIED", returns.apply(firstReturn).status());
        assertEquals(0, QuestRemoteTestResource.credits.get());
        assertEquals(1, transactions.board(MANAGER, true).activeAssignment().progress());
        Pin second = pin(FIELD);
        var complete = new Progress(second, second.assignment().progress()).advance(Map.of(TROLL, 2), false, false);
        ReturnRequest secondReturn = request(second, complete);
        assertEquals("APPLIED", returns.apply(secondReturn).status());
        assertEquals("APPLIED", returns.apply(secondReturn).status());
        assertEquals(1, QuestRemoteTestResource.credits.get());
        assertEquals(120, QuestRemoteTestResource.receipts.get(assignment.assignmentId()).path("gold").longValue());
        assertNull(transactions.board(MANAGER, true).activeAssignment());
        assertEquals("COMPLETED", transactions.board(MANAGER, true).recentAssignments().getFirst().status());
        assertThrows(ClientErrorException.class, () -> returns.apply(request(second, new Progress(second, 2))));
    }
    @Test void lostRewardResponseRecoversFromTheDatabaseWithoutAnotherCredit() {
        Assignment assignment = transactions.accept(UuidV7.next(), MANAGER, KILLS, true);
        Pin pin = pin(FIELD);
        QuestRemoteTestResource.loseResponse.set(true);
        var input = request(pin, new Progress(pin, 3));
        assertEquals("REWARD_PENDING", returns.apply(input).status());
        assertEquals(1, QuestRemoteTestResource.credits.get());
        assertEquals("REWARD_PENDING", transactions.board(MANAGER, true).activeAssignment().status());
        var restarted = new QuestReturns(); restarted.transactions = transactions; restarted.assets = assets;
        restarted.recover(pin.expeditionId());
        assertEquals("APPLIED", transactions.receipt(pin.expeditionId()).status());
        assertEquals(1, QuestRemoteTestResource.credits.get());
        assertNotNull(QuestRemoteTestResource.receipts.get(assignment.assignmentId()));
    }
    @Test void returnReplayIgnoresEligibleMapSetSerializationOrder() {
        UUID definitionId = UuidV7.next();
        var definition = new Definition(definitionId, 1, "Troll patrol", "Defeat three Trolls on either Map.",
                Objective.KILL_COUNT, TROLL, 3, Set.of(FIELD, DUNGEON), new Reward(120, Map.of(), Map.of()));
        QuarkusTransaction.requiringNew().run(() -> {
            var row = new QuestDefinition(); row.id = definitionId; row.definitionId = definitionId; row.version = 1;
            try { row.payload = mapper.writeValueAsString(definition); }
            catch (java.io.IOException invalid) { throw new IllegalStateException(invalid); }
            em.persist(row);
        });
        try {
            transactions.accept(UuidV7.next(), MANAGER, definitionId, true);
            Pin pin = pin(FIELD); ReturnRequest input = request(pin, new Progress(pin, 3));
            assertEquals("APPLIED", returns.apply(input).status());
            QuarkusTransaction.requiringNew().run(() -> {
                var admission = em.find(QuestAdmission.class, pin.expeditionId());
                try {
                    var tree = mapper.readTree(admission.requestJson);
                    var maps = (com.fasterxml.jackson.databind.node.ArrayNode) tree.path("progress").path("pin").path("assignment").path("definition").path("mapIds");
                    // Another JVM may serialize this same unordered set in reverse order.
                    var first = maps.remove(0); maps.add(first);
                    admission.requestJson = mapper.writeValueAsString(tree);
                } catch (java.io.IOException invalid) { throw new IllegalStateException(invalid); }
            });
            assertEquals("APPLIED", returns.apply(input).status());
            assertEquals(1, QuestRemoteTestResource.credits.get());
            assertThrows(ClientErrorException.class, () -> returns.apply(request(pin, new Progress(pin, 2))));
        } finally {
            QuarkusTransaction.requiringNew().run(() -> em.remove(em.find(QuestDefinition.class, definitionId)));
        }
    }
    @Test void assetsOutageRetainsTheAdmissionFenceUntilRecovery() {
        transactions.accept(UuidV7.next(), MANAGER, KILLS, true); Pin pin = pin(FIELD);
        QuestRemoteTestResource.outage.set(true);
        assertEquals("REWARD_PENDING", returns.apply(request(pin, new Progress(pin, 3))).status());
        assertFalse(transactions.board(MANAGER, true).atAgency());
        assertThrows(ClientErrorException.class, () -> transactions.accept(UuidV7.next(), MANAGER, BOSS, true));
        assertThrows(ClientErrorException.class, () -> transactions.release(pin.expeditionId(), MANAGER));
        QuestRemoteTestResource.outage.set(false); returns.recover(pin.expeditionId());
        assertEquals("APPLIED", transactions.receipt(pin.expeditionId()).status());
        assertEquals(1, QuestRemoteTestResource.credits.get());
    }
    @Test void contradictoryReceiptsAreQuarantined() {
        transactions.accept(UuidV7.next(), MANAGER, KILLS, true); Pin pin = pin(FIELD);
        QuestRemoteTestResource.wrongReceipt.set(true);
        assertEquals("CONFLICT", returns.apply(request(pin, new Progress(pin, 3))).status());
        assertEquals("REWARD_PENDING", transactions.board(MANAGER, true).activeAssignment().status());
        assertFalse(transactions.board(MANAGER, true).atAgency());
        returns.recover(pin.expeditionId()); assertEquals(1, QuestRemoteTestResource.credits.get());
    }
    @Test void bossObjectivesOnlyAdvanceOnEligibleMaps() {
        transactions.accept(UuidV7.next(), MANAGER, BOSS, true); Pin field = pin(FIELD);
        var unchanged = new Progress(field, 0).advance(Map.of(TROLL, 1), true, false);
        assertEquals(0, unchanged.progress()); returns.apply(request(field, unchanged));
        Pin dungeon = pin(DUNGEON);
        var completed = new Progress(dungeon, 0).advance(Map.of(TROLL, 1), true, false);
        assertTrue(completed.completed()); assertEquals("APPLIED", returns.apply(request(dungeon, completed)).status());
    }
    @Test void clearingADungeonCreditsItsPinnedGoldAndItemReward() {
        Assignment assignment = transactions.accept(UuidV7.next(), MANAGER, CLEAR, true); Pin pin = pin(DUNGEON);
        var progress = new Progress(pin, 0).advance(Map.of(TROLL, 1), true, true);
        assertEquals("APPLIED", returns.apply(request(pin, progress)).status());
        var command = QuestRemoteTestResource.receipts.get(assignment.assignmentId());
        assertEquals(160, command.path("gold").intValue());
        assertEquals(1, command.path("items").path("019c4c00-0070-7000-8000-000000000002").intValue());
    }
    @Test void exactAcceptanceAndCancellationRetriesBindTheOriginalIntent() {
        UUID key = UuidV7.next(); Assignment accepted = transactions.accept(key, MANAGER, KILLS, true);
        assertTrue(transactions.board(MANAGER, true).recentAssignments().isEmpty());
        assertEquals(accepted, transactions.accept(key, MANAGER, KILLS, false));
        assertThrows(ClientErrorException.class, () -> transactions.accept(key, MANAGER, BOSS, true));
        UUID cancel = UuidV7.next(); Assignment cancelled = transactions.cancel(cancel, MANAGER, accepted.assignmentId(), true);
        assertEquals(cancelled, transactions.cancel(cancel, MANAGER, accepted.assignmentId(), false));
        assertEquals("CANCELLED", cancelled.status());
        assertEquals(List.of(cancelled), transactions.board(MANAGER, true).recentAssignments());
        assertNotEquals(accepted.assignmentId(), transactions.accept(UuidV7.next(), MANAGER, KILLS, true).assignmentId());
    }
    @Test void cancellationAndAcceptanceRequireTheAgencyAndRespectAdmission() {
        assertThrows(ClientErrorException.class, () -> transactions.accept(UuidV7.next(), MANAGER, KILLS, false));
        Assignment assignment = transactions.accept(UuidV7.next(), MANAGER, KILLS, true); Pin pin = pin(FIELD);
        assertThrows(ClientErrorException.class, () -> transactions.cancel(UuidV7.next(), MANAGER, assignment.assignmentId(), true));
        assertThrows(jakarta.ws.rs.NotFoundException.class, () -> transactions.cancel(UuidV7.next(), UuidV7.next(), assignment.assignmentId(), true));
        transactions.release(pin.expeditionId(), MANAGER);
        assertEquals("CANCELLED", transactions.cancel(UuidV7.next(), MANAGER, assignment.assignmentId(), true).status());
    }
    @Test void closeBeforePinPreventsALateAdmissionFromReappearing() {
        UUID expedition = UuidV7.next(); assertTrue(transactions.release(expedition, MANAGER));
        assertThrows(ClientErrorException.class, () -> transactions.pin(new PinRequest(expedition, MANAGER, AGENCY, FIELD, 1)));
        assertFalse(transactions.release(expedition, MANAGER));
    }
    @Test void concurrentAcceptsOnlyCreateOneActiveAssignment() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            var results = new ArrayList<Future<Boolean>>();
            for (int index = 0; index < 2; index++) results.add(executor.submit(() -> {
                start.await(); try { transactions.accept(UuidV7.next(), MANAGER, KILLS, true); return true; }
                catch (ClientErrorException conflict) { return false; }
            }));
            start.countDown(); int accepted = 0; for (var result : results) if (result.get()) accepted++;
            assertEquals(1, accepted);
            assertNotNull(transactions.board(MANAGER, true).activeAssignment());
            assertEquals(1L, QuarkusTransaction.requiringNew().call(() -> em.createQuery("select count(q) from QuestAssignment q where q.managerId = :manager", Long.class).setParameter("manager", MANAGER).getSingleResult()));
        }
    }
    @Test void acceptanceRacingAdmissionEitherPinsTheAssignmentOrRejectsAcceptance() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            Future<Assignment> accept = executor.submit(() -> { start.await(); try { return transactions.accept(UuidV7.next(), MANAGER, KILLS, true); } catch (ClientErrorException conflict) { return null; } });
            Future<Pin> admission = executor.submit(() -> { start.await(); return pin(FIELD); });
            start.countDown(); Assignment assignment = accept.get(); Pin pinned = admission.get();
            assertEquals(assignment == null ? null : assignment.assignmentId(), pinned.assignment() == null ? null : pinned.assignment().assignmentId());
            assertFalse(transactions.board(MANAGER, true).atAgency());
        }
    }
    @Test void privateCommandsRequireTheCorrectServiceCredential() {
        given().contentType("application/json").body(Map.of("expeditionId", UuidV7.next(), "ownerManagerId", MANAGER, "agencyId", AGENCY, "mapId", FIELD, "mapVersion", 1))
                .post("/internal/v1/quests/admissions").then().statusCode(403);
        given().contentType("application/json").header("X-Hero-Association-Quest-Core-Service-Key", "test-quest-core-service-key-012345678901234567")
                .body(Map.of("expeditionId", UuidV7.next(), "ownerManagerId", MANAGER, "agencyId", AGENCY, "mapId", FIELD, "mapVersion", 1))
                .post("/internal/v1/quests/admissions").then().statusCode(403);
    }
    @Test @TestSecurity(user = "player") void thePublicBoardAndCommandsResolveTheManagerThroughCore() {
        given().get("/api/v1/quests").then().statusCode(200);
        given().contentType("application/json").body(Map.of("commandId", UuidV7.next())).post("/api/v1/quests/" + KILLS + "/accept").then().statusCode(200);
        assertNotNull(transactions.board(MANAGER, true).activeAssignment());
    }
    private Pin pin(UUID map) { return transactions.pin(new PinRequest(UuidV7.next(), MANAGER, AGENCY, map, 1)); }
    private ReturnRequest request(Pin pin, Progress progress) { return new ReturnRequest(pin.expeditionId(), pin.ownerManagerId(), pin.agencyId(), pin.mapId(), pin.mapVersion(), progress); }
}
