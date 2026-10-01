package io.tiagovibeson.heroassociation.application;

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
import io.tiagovibeson.heroassociation.application.exception.AssetOperationRejectedException;
import io.tiagovibeson.heroassociation.domain.AssetOperationReceipt;
import io.tiagovibeson.heroassociation.domain.AssetOwnerType;
import io.tiagovibeson.heroassociation.domain.AssetReservation;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import io.tiagovibeson.heroassociation.repository.AgencyItemRepository;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.ManagerItemRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRepository;
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
    @Inject ManagerRepository managerRepository;
    @Inject AgencyRepository agencyRepository;
    @Inject ManagerItemRepository managerItemRepository;
    @Inject AgencyItemRepository agencyItemRepository;

    @Test
    @TestTransaction
    void goldReservationAndReleaseAreIdempotent() {
        long startingGold = managerRepository.findById(MANAGER_ID).getGold();
        UUID reservationKey = UuidV7.next();
        UUID releaseKey = UuidV7.next();

        AssetReservation reservation = assetsService.reserveGold(reservationKey,
                AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 1, 120);
        assertEquals(reservation.getId(), assetsService.reserveGold(reservationKey,
                AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 1, 120).getId());
        assertEquals(startingGold - 120, managerRepository.findById(MANAGER_ID).getGold());
        assertThrows(AssetOperationRejectedException.class, () -> assetsService.reserveGold(
                reservationKey, AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 1, 121));

        AssetOperationReceipt receipt = assetsService.release(releaseKey, reservationKey, 1);
        assertEquals(receipt.getId(), assetsService.release(releaseKey, reservationKey, 1).getId());
        assertEquals(0, reservation.getRemainingQuantity());
        assertEquals(startingGold, managerRepository.findById(MANAGER_ID).getGold());
        assertThrows(AssetOperationRejectedException.class,
                () -> assetsService.release(UuidV7.next(), reservationKey, 1));
        assertThrows(AssetOperationRejectedException.class,
                () -> assetsService.release(releaseKey, reservationKey, 2));
    }

    @Test
    @TestTransaction
    void partialSettlementRefundsPriceImprovementAndReleasesOnlyTheRemainder() {
        long buyerGold = managerRepository.findById(MANAGER_ID).getGold();
        long sellerGold = agencyRepository.findById(AGENCY_ID).getGold();
        int sellerItems = agencyItemRepository.findForUpdate(AGENCY_ID, CRYSTAL_ID).orElseThrow().getQuantity();
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
        assertEquals(buyerGold - 160, managerRepository.findById(MANAGER_ID).getGold());
        assertEquals(sellerItems - 2,
                agencyItemRepository.findForUpdate(AGENCY_ID, CRYSTAL_ID).orElseThrow().getQuantity());

        assertThrows(AssetOperationRejectedException.class,
                () -> assetsService.settleTrade(UuidV7.next(), buyKey, sellKey, 1, 59));
        AssetOperationReceipt trade = assetsService.settleTrade(tradeKey, buyKey, sellKey, 1, 70);
        assertEquals(trade.getId(), assetsService.settleTrade(tradeKey, buyKey, sellKey, 1, 70).getId());
        assertEquals(1, buy.getRemainingQuantity());
        assertEquals(1, sell.getRemainingQuantity());
        assertEquals(buyerGold - 150, managerRepository.findById(MANAGER_ID).getGold());
        assertEquals(sellerGold + 63, agencyRepository.findById(AGENCY_ID).getGold());
        assertEquals(1, managerItemRepository.findForUpdate(MANAGER_ID, CRYSTAL_ID)
                .orElseThrow().getQuantity());
        assertThrows(AssetOperationRejectedException.class,
                () -> assetsService.settleTrade(tradeKey, buyKey, sellKey, 1, 71));

        assetsService.release(UuidV7.next(), buyKey, 1);
        assetsService.release(UuidV7.next(), sellKey, 1);
        assertEquals(buyerGold - 70, managerRepository.findById(MANAGER_ID).getGold());
        assertEquals(sellerGold + 63, agencyRepository.findById(AGENCY_ID).getGold());
        assertEquals(sellerItems - 1,
                agencyItemRepository.findForUpdate(AGENCY_ID, CRYSTAL_ID).orElseThrow().getQuantity());
        assertEquals(0, buy.getRemainingQuantity());
        assertEquals(0, sell.getRemainingQuantity());
    }

    @Test
    @TestTransaction
    void agencyCanBuyFromManagerWithinBothPriceLimits() {
        long buyerGold = agencyRepository.findById(AGENCY_ID).getGold();
        long sellerGold = managerRepository.findById(SELLER_MANAGER_ID).getGold();
        int buyerItems = agencyItemRepository.findForUpdate(AGENCY_ID, CRYSTAL_ID)
                .orElseThrow().getQuantity();
        int sellerItems = managerItemRepository.findForUpdate(SELLER_MANAGER_ID, CRYSTAL_ID)
                .orElseThrow().getQuantity();
        UUID buyKey = UuidV7.next();
        UUID sellKey = UuidV7.next();

        assetsService.reserveGold(buyKey, AssetOwnerType.AGENCY,
                AGENCY_ID, CRYSTAL_ID, 1, 120);
        assetsService.reserveItems(sellKey, AssetOwnerType.MANAGER,
                SELLER_MANAGER_ID, CRYSTAL_ID, 1, 100);
        assetsService.settleTrade(UuidV7.next(), buyKey, sellKey, 1, 110);

        assertEquals(buyerGold - 110, agencyRepository.findById(AGENCY_ID).getGold());
        assertEquals(sellerGold + 99, managerRepository.findById(SELLER_MANAGER_ID).getGold());
        assertEquals(buyerItems + 1, agencyItemRepository.findForUpdate(AGENCY_ID, CRYSTAL_ID)
                .orElseThrow().getQuantity());
        assertEquals(sellerItems - 1, managerItemRepository.findForUpdate(SELLER_MANAGER_ID, CRYSTAL_ID)
                .orElseThrow().getQuantity());
    }

    @Test
    @TestTransaction
    void rejectsOverspendingAndInvalidKeysWithoutChangingBalances() {
        long buyerGold = managerRepository.findById(MANAGER_ID).getGold();
        int sellerItems = agencyItemRepository.findForUpdate(AGENCY_ID, CRYSTAL_ID).orElseThrow().getQuantity();

        assertThrows(AssetOperationRejectedException.class, () -> assetsService.reserveGold(
                UuidV7.next(), AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 3, 100));
        assertThrows(AssetOperationRejectedException.class, () -> assetsService.reserveItems(
                UuidV7.next(), AssetOwnerType.AGENCY, AGENCY_ID, CRYSTAL_ID, sellerItems + 1, 60));
        assertThrows(AssetOperationRejectedException.class, () -> assetsService.reserveGold(
                UUID.randomUUID(), AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 1, 1));
        assertThrows(AssetOperationRejectedException.class, () -> assetsService.reserveGold(
                UuidV7.next(), AssetOwnerType.MANAGER, MANAGER_ID, CRYSTAL_ID, 2, Long.MAX_VALUE));

        assertEquals(buyerGold, managerRepository.findById(MANAGER_ID).getGold());
        assertEquals(sellerItems,
                agencyItemRepository.findForUpdate(AGENCY_ID, CRYSTAL_ID).orElseThrow().getQuantity());
    }

    @Test
    void concurrentReservationsCannotSpendTheSameGoldTwice() throws Exception {
        UUID firstKey = UuidV7.next();
        UUID secondKey = UuidV7.next();
        long startingGold = managerRepository.findById(MANAGER_ID).getGold();
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
            assertEquals(startingGold - 150, managerRepository.findById(MANAGER_ID).getGold());

            UUID reservedKey = firstSucceeded ? firstKey : secondKey;
            assetsService.release(UuidV7.next(), reservedKey, 1);
            entityManager.clear();
            assertEquals(startingGold, managerRepository.findById(MANAGER_ID).getGold());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentReleasesCannotCreditTwice() throws Exception {
        UUID reservationKey = UuidV7.next();
        long startingGold = managerRepository.findById(MANAGER_ID).getGold();
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
            assertEquals(startingGold, managerRepository.findById(MANAGER_ID).getGold());
        } finally {
            start.countDown();
            executor.shutdownNow();
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
