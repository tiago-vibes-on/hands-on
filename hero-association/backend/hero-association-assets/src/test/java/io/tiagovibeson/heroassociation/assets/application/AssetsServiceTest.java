package io.tiagovibeson.heroassociation.assets.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.assets.application.exception.AssetOperationRejectedException;
import io.tiagovibeson.heroassociation.assets.domain.AssetOperationReceipt;
import io.tiagovibeson.heroassociation.assets.domain.AssetOwnerType;
import io.tiagovibeson.heroassociation.assets.domain.AssetReservation;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import io.tiagovibeson.heroassociation.assets.domain.AssetWallet;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AssetsServiceTest {

    private static final UUID MANAGER_ID = UUID.fromString("019c4c00-0000-7000-8000-000000000204");
    private static final UUID SELLER_MANAGER_ID = UUID.fromString("019c4c00-0000-7000-8000-000000000203");
    private static final UUID AGENCY_ID = UUID.fromString("019c4c00-0001-7000-8000-000000000001");
    private static final UUID CRYSTAL_ID = UUID.fromString("019c4c00-0070-7000-8000-000000000001");

    @Inject AssetsService assetsService;
    @Inject EntityManager entityManager;
    @Inject AssetSnapshots snapshots;
    @Inject AssetBalances balances;

    @Test
    @TestTransaction
    void goldReservationAndReleaseAreIdempotent() {
        long startingGold = gold(MANAGER_ID);
        UUID reservationKey = UuidV7.next();
        UUID releaseKey = UuidV7.next();

        AssetReservation reservation = assetsService.reserveGold(reservationKey,
                AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 1, 120);
        assertEquals(reservation.getId(), assetsService.reserveGold(reservationKey,
                AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 1, 120).getId());
        assertEquals(startingGold - 120, gold(MANAGER_ID));
        assertThrows(AssetOperationRejectedException.class, () -> assetsService.reserveGold(
                reservationKey, AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 1, 121));

        AssetOperationReceipt receipt = assetsService.release(releaseKey, reservationKey, 1);
        assertEquals(receipt.getId(), assetsService.release(releaseKey, reservationKey, 1).getId());
        assertEquals(0, reservation.getRemainingQuantity());
        assertEquals(startingGold, gold(MANAGER_ID));
        assertThrows(AssetOperationRejectedException.class,
                () -> assetsService.release(UuidV7.next(), reservationKey, 1));
        assertThrows(AssetOperationRejectedException.class,
                () -> assetsService.release(releaseKey, reservationKey, 2));
    }

    @Test
    @TestTransaction
    void partialSettlementRefundsPriceImprovementAndReleasesOnlyTheRemainder() {
        long buyerGold = gold(MANAGER_ID);
        long sellerGold = gold(AGENCY_ID);
        int sellerItems = quantity(AGENCY_ID, CRYSTAL_ID);
        UUID buyKey = UuidV7.next();
        UUID sellKey = UuidV7.next();
        UUID tradeKey = UuidV7.next();

        AssetReservation buy = assetsService.reserveGold(buyKey, AssetOwnerType.MANAGER,
                MANAGER_ID, CRYSTAL_ID, 2, 80);
        AssetReservation sell = assetsService.reserveItems(sellKey, AssetOwnerType.AGENCY,
                AGENCY_ID, CRYSTAL_ID, 2, 60);
        assertEquals(buy.getId(), assetsService.reserveGold(buyKey, AssetOwnerType.MANAGER,
                MANAGER_ID, CRYSTAL_ID, 2, 80).getId());
        assertEquals(sell.getId(), assetsService.reserveItems(sellKey, AssetOwnerType.AGENCY,
                AGENCY_ID, CRYSTAL_ID, 2, 60).getId());
        assertEquals(buyerGold - 160, gold(MANAGER_ID));
        assertEquals(sellerItems - 2,
                quantity(AGENCY_ID, CRYSTAL_ID));

        assertThrows(AssetOperationRejectedException.class,
                () -> assetsService.settleTrade(UuidV7.next(), buyKey, sellKey, 1, 59));
        AssetOperationReceipt trade = assetsService.settleTrade(tradeKey, buyKey, sellKey, 1, 70);
        assertEquals(trade.getId(), assetsService.settleTrade(tradeKey, buyKey, sellKey, 1, 70).getId());
        assertEquals(1, buy.getRemainingQuantity());
        assertEquals(1, sell.getRemainingQuantity());
        assertEquals(buyerGold - 150, gold(MANAGER_ID));
        assertEquals(sellerGold + 63, gold(AGENCY_ID));
        assertEquals(1, quantity(MANAGER_ID, CRYSTAL_ID));
        assertThrows(AssetOperationRejectedException.class,
                () -> assetsService.settleTrade(tradeKey, buyKey, sellKey, 1, 71));

        assetsService.release(UuidV7.next(), buyKey, 1);
        assetsService.release(UuidV7.next(), sellKey, 1);
        assertEquals(buyerGold - 70, gold(MANAGER_ID));
        assertEquals(sellerGold + 63, gold(AGENCY_ID));
        assertEquals(sellerItems - 1,
                quantity(AGENCY_ID, CRYSTAL_ID));
        assertEquals(0, buy.getRemainingQuantity());
        assertEquals(0, sell.getRemainingQuantity());
    }

    @Test
    @TestTransaction
    void agencyCanBuyFromManagerWithinBothPriceLimits() {
        long buyerGold = gold(AGENCY_ID);
        long sellerGold = gold(SELLER_MANAGER_ID);
        int buyerItems = quantity(AGENCY_ID, CRYSTAL_ID);
        int sellerItems = quantity(SELLER_MANAGER_ID, CRYSTAL_ID);
        UUID buyKey = UuidV7.next();
        UUID sellKey = UuidV7.next();

        assetsService.reserveGold(buyKey, AssetOwnerType.AGENCY,
                AGENCY_ID, CRYSTAL_ID, 1, 120);
        assetsService.reserveItems(sellKey, AssetOwnerType.MANAGER,
                SELLER_MANAGER_ID, CRYSTAL_ID, 1, 100);
        assetsService.settleTrade(UuidV7.next(), buyKey, sellKey, 1, 110);

        assertEquals(buyerGold - 110, gold(AGENCY_ID));
        assertEquals(sellerGold + 99, gold(SELLER_MANAGER_ID));
        assertEquals(buyerItems + 1, quantity(AGENCY_ID, CRYSTAL_ID));
        assertEquals(sellerItems - 1, quantity(SELLER_MANAGER_ID, CRYSTAL_ID));
    }

    @Test
    @TestTransaction
    void rejectsOverspendingAndInvalidKeysWithoutChangingBalances() {
        long buyerGold = gold(MANAGER_ID);
        int sellerItems = quantity(AGENCY_ID, CRYSTAL_ID);

        assertThrows(AssetOperationRejectedException.class, () -> assetsService.reserveGold(
                UuidV7.next(), AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 3, 100));
        assertThrows(AssetOperationRejectedException.class, () -> assetsService.reserveItems(
                UuidV7.next(), AssetOwnerType.AGENCY, AGENCY_ID, CRYSTAL_ID, sellerItems + 1, 60));
        assertThrows(AssetOperationRejectedException.class, () -> assetsService.reserveGold(
                UUID.randomUUID(), AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 1, 1));
        assertThrows(AssetOperationRejectedException.class, () -> assetsService.reserveGold(
                UuidV7.next(), AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 2, Long.MAX_VALUE));

        assertEquals(buyerGold, gold(MANAGER_ID));
        assertEquals(sellerItems,
                quantity(AGENCY_ID, CRYSTAL_ID));
    }

    @Test
    void concurrentReservationsCannotSpendTheSameGoldTwice() throws Exception {
        UUID firstKey = UuidV7.next();
        UUID secondKey = UuidV7.next();
        long startingGold = gold(MANAGER_ID);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> reserveAfterStart(firstKey, ready, start));
            Future<Boolean> second = executor.submit(() -> reserveAfterStart(secondKey, ready, start));
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Reservation workers did not start.");
            }
            start.countDown();
            boolean firstSucceeded = first.get(15, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(15, TimeUnit.SECONDS);
            assertEquals(1, (firstSucceeded ? 1 : 0) + (secondSucceeded ? 1 : 0));
            entityManager.clear();
            assertEquals(startingGold - 150, gold(MANAGER_ID));

            UUID reservedKey = firstSucceeded ? firstKey : secondKey;
            assetsService.release(UuidV7.next(), reservedKey, 1);
            entityManager.clear();
            assertEquals(startingGold, gold(MANAGER_ID));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentReleasesCannotCreditTwice() throws Exception {
        UUID reservationKey = UuidV7.next();
        long startingGold = gold(MANAGER_ID);
        assetsService.reserveGold(reservationKey, AssetOwnerType.MANAGER,
                MANAGER_ID, CRYSTAL_ID, 1, 100);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(
                    () -> releaseAfterStart(UuidV7.next(), reservationKey, ready, start));
            Future<Boolean> second = executor.submit(
                    () -> releaseAfterStart(UuidV7.next(), reservationKey, ready, start));
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Release workers did not start.");
            }
            start.countDown();
            boolean firstSucceeded = first.get(15, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(15, TimeUnit.SECONDS);
            assertEquals(1, (firstSucceeded ? 1 : 0) + (secondSucceeded ? 1 : 0));
            entityManager.clear();
            assertEquals(startingGold, gold(MANAGER_ID));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @TestTransaction
    void closingAnUnknownReservationPermanentlyFencesLatePlacement() {
        UUID reservationKey = UuidV7.next();
        UUID operationKey = UuidV7.next();
        long gold = gold(MANAGER_ID);
        var receipt = assetsService.close(operationKey, reservationKey);
        assertEquals(0, receipt.getQuantity());
        assertEquals(receipt.getId(), assetsService.close(operationKey, reservationKey).getId());
        assertThrows(AssetOperationRejectedException.class, () -> assetsService.reserveGold(
                reservationKey, AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 1, 10));
        assertEquals(gold, gold(MANAGER_ID));
    }

    @Test
    @TestTransaction
    void closingAnExistingReservationRefundsOnlyItsRemainingQuantityOnce() {
        UUID reservationKey = UuidV7.next();
        UUID operationKey = UuidV7.next();
        long gold = gold(MANAGER_ID);
        assetsService.reserveGold(reservationKey, AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 2, 10);
        assetsService.release(UuidV7.next(), reservationKey, 1);
        var receipt = assetsService.close(operationKey, reservationKey);
        assertEquals(1, receipt.getQuantity());
        assertEquals(receipt.getId(), assetsService.close(operationKey, reservationKey).getId());
        assertEquals(gold, gold(MANAGER_ID));
        assertThrows(AssetOperationRejectedException.class, () -> assetsService.reserveGold(
                reservationKey, AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 2, 10));
    }

    @Test
    void closingAndReservingConcurrentlyLeavesNoGoldStranded() throws Exception {
        UUID reservationKey = UuidV7.next();
        long gold = gold(MANAGER_ID);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> reserve = executor.submit(() -> {
                await(start);
                try {
                    assetsService.reserveGold(reservationKey, AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 1, 10);
                } catch (AssetOperationRejectedException expectedIfCloseWins) {
                    assertEquals("The reservation was permanently closed.", expectedIfCloseWins.getMessage());
                }
            });
            Future<?> close = executor.submit(() -> {
                await(start);
                assetsService.close(UuidV7.next(), reservationKey);
            });
            start.countDown();
            reserve.get(15, TimeUnit.SECONDS);
            close.get(15, TimeUnit.SECONDS);
        }
        entityManager.clear();
        assertEquals(gold, gold(MANAGER_ID));
    }

    @Test
    void concurrentExactReleaseRetriesReturnOneReceipt() throws Exception {
        UUID reservationKey = UuidV7.next();
        UUID operationKey = UuidV7.next();
        long gold = gold(MANAGER_ID);
        assetsService.reserveGold(reservationKey, AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 1, 10);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { await(start); return assetsService.release(operationKey, reservationKey, 1).getId(); });
            var second = executor.submit(() -> { await(start); return assetsService.release(operationKey, reservationKey, 1).getId(); });
            start.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
        entityManager.clear();
        assertEquals(gold, gold(MANAGER_ID));
    }

    private AssetSnapshots.OwnerSnapshot owner(UUID id) { return snapshots.read(new AssetSnapshots.SnapshotRequest(java.util.List.of(new AssetSnapshots.OwnerRequest(id.equals(AGENCY_ID) ? AssetOwnerType.AGENCY : AssetOwnerType.MANAGER, id)), java.util.List.of())).owners().getFirst(); }
    private long gold(UUID id) { return owner(id).gold(); }
    private int quantity(UUID owner, UUID item) { return owner(owner).items().stream().filter(entry -> entry.item().id().equals(item)).mapToInt(entry -> entry.quantity()).findFirst().orElse(0); }

    private static void await(CountDownLatch start) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Workers did not start.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private boolean releaseAfterStart(UUID operationKey, UUID reservationKey,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Release workers were not released.");
        }
        try {
            assetsService.release(operationKey, reservationKey, 1);
            return true;
        } catch (AssetOperationRejectedException exception) {
            return false;
        }
    }

    private boolean reserveAfterStart(UUID key, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Reservation workers were not released.");
        }
        try {
            assetsService.reserveGold(key, AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 1, 150);
            return true;
        } catch (AssetOperationRejectedException exception) {
            return false;
        }
    }
}
