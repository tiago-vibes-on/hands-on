package io.tiagovibeson.heroassociation.market;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.equalTo;
import static io.tiagovibeson.heroassociation.market.AssetsStubResource.*;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import io.tiagovibeson.heroassociation.market.MarketContracts.*;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@QuarkusTest @QuarkusTestResource(AssetsStubResource.class)
@TestSecurity(user = "player-a")
class MarketWorkflowTest {
    @Inject MarketTransactions transactions;
    @Inject MarketCoordinator coordinator;
    @Inject EntityManager em;
    @BeforeEach void resetDatabaseAndLedger() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("delete from MarketTrade").executeUpdate(); em.createQuery("delete from MarketOrder").executeUpdate();
            em.createQuery("delete from MarketPlacement").executeUpdate(); em.createQuery("delete from MarketBook").executeUpdate();
        }); reset(); items.put(BUYER, 5);
    }
    @Test @TestSecurity(authorizationEnabled = true) void anonymousCallsFailClosed() {
        given().get("/api/v1/market/orders").then().statusCode(401);
    }
    @Test void invalidAndDuplicateRequestsDoNotCreateAnotherReservation() {
        given().contentType(ContentType.JSON).body("{}").post("/api/v1/market/orders").then().statusCode(400);
        UUID id = UuidV7.next(); var request = buy(id, 1, 10);
        var first = post(request); first.then().statusCode(201);
        post(request).then().statusCode(201).body("id", equalTo(first.jsonPath().getString("id")));
        post(buy(id, 1, 11)).then().statusCode(409);
        assertEquals(190L, gold.get(BUYER)); assertEquals(1, debits);
    }
    @Test void lostReservationConfirmationRecoversAfterAWorkerRestart() {
        reserveResponsesLost = 1; UUID id = UuidV7.next();
        post(buy(id, 1, 10)).then().statusCode(202).body("status", equalTo("PENDING_RESERVATION"));
        assertEquals(190L, gold.get(BUYER)); due(); coordinator.recover();
        given().get("/api/v1/market/placements/" + id).then().statusCode(200).body("status", equalTo("OPEN"));
        assertEquals(1, debits); assertEquals(1, transactions.book().size());
    }
    @Test void anUnconfirmedPlacementIsFencedBeforeItBecomesAbandoned() {
        reserveUnavailable = true; UUID id = UuidV7.next();
        post(buy(id, 1, 10)).then().statusCode(202);
        reserveUnavailable = false; due(); coordinator.recover();
        var placement = transactions.placement(id);
        assertEquals(PlacementState.ABANDONED, placement.state); assertTrue(closed.contains(placement.reservationKey));
        post(buy(id, 1, 10)).then().statusCode(409); assertEquals(200L, gold.get(BUYER));
    }
    @Test void insufficientGoldLeavesNoOrderAndHasAConfirmedClosure() {
        UUID id = UuidV7.next(); post(buy(id, 1000, 100)).then().statusCode(409);
        var placement = transactions.placement(id);
        assertEquals(PlacementState.REJECTED, placement.state); assertTrue(closed.contains(placement.reservationKey));
        assertTrue(transactions.book().isEmpty()); assertEquals(200L, gold.get(BUYER));
    }
    @Test void priceTimeMatchingUsesTheRestingPriceAndPaysTheFeeOnce() {
        UUID firstSeller = sell(SELLER, "seller-token", 1, 60);
        UUID secondSeller = sell(AGENCY, "market-test-player-token", 1, 60);
        UUID buyer = UUID.fromString(post(buy(UuidV7.next(), 1, 80)).jsonPath().getString("id"));
        coordinator.recover();
        assertEquals("FILLED", transactions.order(firstSeller).status());
        assertEquals("OPEN", transactions.order(secondSeller).status());
        assertEquals("FILLED", transactions.order(buyer).status());
        assertEquals(140L, gold.get(BUYER)); assertEquals(54L, gold.get(SELLER)); assertEquals(6, items.get(BUYER));
    }
    @Test void lostSettlementCannotRematchItsQuantityAndCancellationWaitsForIt() {
        UUID seller = sell(SELLER, "seller-token", 2, 60);
        UUID buyer = UUID.fromString(post(buy(UuidV7.next(), 1, 80)).jsonPath().getString("id"));
        settlementResponsesLost = 1; coordinator.recover();
        assertEquals(1, transactions.order(buyer).quantityPending());
        assertEquals(140L, gold.get(BUYER));
        given().delete("/api/v1/market/orders/" + buyer).then().statusCode(202).body("status", equalTo("PENDING_CANCEL"));
        due(); coordinator.recover();
        assertEquals("CANCELLED", transactions.order(buyer).status());
        assertEquals(0, transactions.order(buyer).quantityPending());
        assertEquals(1, transactions.order(seller).quantityRemaining());
        assertEquals(54L, gold.get(SELLER)); assertEquals(140L, gold.get(BUYER));
    }
    @Test void repeatedCancellationRecoversALostClosureWithoutAnotherRefund() {
        UUID buyer = UUID.fromString(post(buy(UuidV7.next(), 2, 10)).jsonPath().getString("id"));
        closeResponsesLost = 1;
        given().delete("/api/v1/market/orders/" + buyer).then().statusCode(202);
        assertEquals(200L, gold.get(BUYER)); due(); coordinator.recover();
        given().delete("/api/v1/market/orders/" + buyer).then().statusCode(200).body("status", equalTo("CANCELLED"));
        assertEquals(200L, gold.get(BUYER));
    }
    @Test void concurrentPlacementsWithOneIdProduceOneOrder() throws Exception {
        var request = buy(UuidV7.next(), 1, 10); CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return post(request).statusCode(); });
            var second = executor.submit(() -> { start.await(); return post(request).statusCode(); });
            start.countDown(); assertTrue(java.util.Set.of(201, 202).contains(first.get(15, TimeUnit.SECONDS)));
            assertTrue(java.util.Set.of(201, 202).contains(second.get(15, TimeUnit.SECONDS)));
        }
        assertEquals(1, transactions.book().size()); assertEquals(1, debits);
    }
    @Test void anExpiredWorkerCannotPublishAfterAnotherWorkerAbandonsThePlacement() {
        var request = buy(UuidV7.next(), 1, 10);
        transactions.stage("player-a", request, context(BUYER, OwnerType.MANAGER));
        var oldClaim = transactions.claimPlacement(request.placementId(), Instant.now());
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update market_placement set lease_until = :past where id = :id")
                .setParameter("past", Instant.EPOCH).setParameter("id", request.placementId()).executeUpdate());
        coordinator.recover(); transactions.publish(oldClaim);
        assertEquals(PlacementState.ABANDONED, transactions.placement(request.placementId()).state);
        assertTrue(transactions.book().isEmpty()); assertEquals(200L, gold.get(BUYER));
    }
    @Test void selfTradingKeepsBothReservationsAndCreatesNoTrade() {
        UUID seller = sell(BUYER, "market-test-player-token", 1, 10);
        UUID buyer = UUID.fromString(post(buy(UuidV7.next(), 1, 10)).jsonPath().getString("id"));
        coordinator.recover();
        assertEquals("OPEN", transactions.order(seller).status());
        assertEquals("OPEN", transactions.order(buyer).status());
        assertEquals(0, transactions.pendingCount("trade"));
    }
    @Test void partialFillThenCancellationReturnsOnlyTheUnmatchedGold() {
        sell(SELLER, "seller-token", 1, 10);
        UUID buyer = UUID.fromString(post(buy(UuidV7.next(), 2, 20)).jsonPath().getString("id"));
        coordinator.recover();
        assertEquals("PARTIALLY_FILLED", transactions.order(buyer).status());
        assertEquals(170L, gold.get(BUYER));
        given().delete("/api/v1/market/orders/" + buyer).then().statusCode(200);
        assertEquals(190L, gold.get(BUYER)); assertEquals(9L, gold.get(SELLER)); assertEquals(6, items.get(BUYER));
    }
    @Test void concurrentWorkersConsumeATradeOnlyOnce() throws Exception {
        sell(SELLER, "seller-token", 1, 10);
        post(buy(UuidV7.next(), 1, 20)).then().statusCode(201);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); coordinator.recover(); return true; });
            var second = executor.submit(() -> { start.await(); coordinator.recover(); return true; });
            start.countDown(); assertTrue(first.get(15, TimeUnit.SECONDS)); assertTrue(second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(190L, gold.get(BUYER)); assertEquals(9L, gold.get(SELLER)); assertEquals(6, items.get(BUYER));
        assertEquals(0, transactions.pendingCount("trade"));
    }
    @Test void anotherSubjectCannotReuseOrReadAPlacement() {
        var request = buy(UuidV7.next(), 1, 10); post(request).then().statusCode(201);
        var facade = io.quarkus.arc.Arc.container().instance(MarketFacade.class).get();
        assertEquals(403, assertThrows(MarketException.class, () -> facade.placement("other-player", request.placementId())).getResponse().getStatus());
        assertEquals(409, assertThrows(MarketException.class, () -> facade.place("other-player", "other-token", request)).getResponse().getStatus());
    }
    @Test void aConflictingTradeKeepsItsOrderFrozenWhenAnotherAllocatedTradeCompletes() {
        sell(SELLER, "seller-token", 1, 10); sell(AGENCY, "market-test-player-token", 1, 10);
        UUID buyer = UUID.fromString(post(buy(UuidV7.next(), 2, 10)).jsonPath().getString("id"));
        var trades = QuarkusTransaction.requiringNew().call(() -> em.createQuery("from MarketTrade order by id", MarketTrade.class).getResultList());
        var inconsistent = trades.get(0);
        receipts.put(inconsistent.id, new Receipt(inconsistent.id, "TRADE_SETTLEMENT", inconsistent.buyerReservationKey,
                inconsistent.sellerReservationKey, 99, inconsistent.priceGoldPerItem));
        coordinator.trade(inconsistent.id);
        coordinator.trade(trades.get(1).id);
        assertEquals("CONFLICT", transactions.order(buyer).status());
        assertEquals(1, transactions.order(buyer).quantityPending());
        assertTrue(transactions.book().stream().noneMatch(order -> order.id().equals(buyer)));
    }
    @Test void cancellationAgeStartsWhenCancellationBeginsRatherThanWhenTheOrderWasPlaced() {
        UUID buyer = UUID.fromString(post(buy(UuidV7.next(), 1, 10)).jsonPath().getString("id"));
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update market_order set created_at = :old where id = :id")
                .setParameter("old", Instant.now().minusSeconds(86400)).setParameter("id", buyer).executeUpdate());
        closeResponsesLost = 1;
        given().delete("/api/v1/market/orders/" + buyer).then().statusCode(202);
        assertTrue(transactions.oldestPendingSeconds("cancellation", Instant.now()) < 5);
    }
    private PlaceRequest buy(UUID id, int quantity, long price) { return new PlaceRequest(id, OwnerType.MANAGER, null, Side.BUY, ITEM, quantity, price); }
    private io.restassured.response.Response post(PlaceRequest request) {
        return given().contentType(ContentType.JSON).body(request).post("/api/v1/market/orders");
    }
    private OwnerContext context(UUID owner, OwnerType type) {
        return new OwnerContext(owner == SELLER ? SELLER : BUYER, type, owner, "Owner " + owner,
                new ItemDefinition(ITEM, "magic-crystal", "Magic Crystal", "✦"));
    }
    private UUID sell(UUID owner, String token, int quantity, long price) {
        OwnerType type = owner.equals(AGENCY) ? OwnerType.AGENCY : OwnerType.MANAGER;
        var request = new PlaceRequest(UuidV7.next(), type, type == OwnerType.AGENCY ? owner : null, Side.SELL, ITEM, quantity, price);
        var placement = transactions.stage("seller", request, context(owner, type)); coordinator.placement(placement.id, token);
        return transactions.placement(placement.id).orderId;
    }
    private void due() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (String entity : new String[]{"MarketPlacement", "MarketTrade", "MarketOrder"})
                em.createQuery("update " + entity + " set nextAttemptAt = :now").setParameter("now", Instant.EPOCH).executeUpdate();
        });
    }
}
