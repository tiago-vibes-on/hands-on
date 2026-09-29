package io.tiagovibeson.heroassociation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.tiagovibeson.heroassociation.application.exception.MarketOrderOwnerRequiredException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.AgencyItem;
import io.tiagovibeson.heroassociation.domain.Item;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.domain.MarketOrder;
import io.tiagovibeson.heroassociation.domain.MarketOrderSide;
import io.tiagovibeson.heroassociation.domain.MarketOwnerType;
import io.tiagovibeson.heroassociation.repository.AgencyItemRepository;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.ItemRepository;
import io.tiagovibeson.heroassociation.repository.ManagerItemRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRepository;
import io.tiagovibeson.heroassociation.repository.MarketOrderRepository;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "019c4c00-0100-7000-8000-000000000104")
class MarketOrderOwnershipServiceTest {

    private static final UUID DAWNWATCH = UUID.fromString("019c4c00-0001-7000-8000-000000000001");
    private static final UUID MANAGER_4 = UUID.fromString("019c4c00-0000-7000-8000-000000000204");
    private static final UUID MAGIC_CRYSTAL = UUID.fromString("019c4c00-0070-7000-8000-000000000001");
    private static final UUID IRON_INGOT = UUID.fromString("019c4c00-0070-7000-8000-000000000002");

    @Inject MarketOrderService service;
    @Inject MarketOrderRepository orderRepository;
    @Inject ManagerRepository managerRepository;
    @Inject ManagerItemRepository managerItemRepository;
    @Inject AgencyRepository agencyRepository;
    @Inject AgencyItemRepository agencyItemRepository;
    @Inject ItemRepository itemRepository;

    @Test
    @TestTransaction
    void personalBuyUsesManagerWalletAndInventoryWithPriceImprovement() {
        Agency agency = agencyRepository.findById(DAWNWATCH);
        Manager manager = managerRepository.findById(MANAGER_4);
        Item item = itemRepository.findById(MAGIC_CRYSTAL);
        long agencyGoldBefore = agency.getGold();
        long managerGoldBefore = manager.getGold();
        int managerItemsBefore = managerItemRepository.findForUpdate(MANAGER_4, MAGIC_CRYSTAL)
                .map(entry -> entry.getQuantity()).orElse(0);

        agencyItemRepository.findForUpdate(DAWNWATCH, MAGIC_CRYSTAL).orElseThrow().decreaseQuantity(1);
        orderRepository.persist(new MarketOrder(agency, item, MarketOrderSide.SELL, 1, 100));

        var result = service.createOrder(MarketOwnerType.MANAGER, null, MarketOrderSide.BUY, MAGIC_CRYSTAL, 1, 120);

        assertEquals("FILLED", result.status());
        assertEquals("MANAGER", result.ownerType());
        assertEquals(MANAGER_4, result.ownerId());
        assertEquals(managerGoldBefore - 100, manager.getGold());
        assertEquals(agencyGoldBefore + 90, agency.getGold());
        assertEquals(managerItemsBefore + 1,
                managerItemRepository.findForUpdate(MANAGER_4, MAGIC_CRYSTAL).orElseThrow().getQuantity());
    }

    @Test
    @TestTransaction
    void personalSellCreditsManagerAfterMarketFeeAndTransfersItemToAgency() {
        Agency agency = agencyRepository.findById(DAWNWATCH);
        Manager manager = managerRepository.findById(MANAGER_4);
        Item item = itemRepository.findById(IRON_INGOT);
        long agencyGoldBefore = agency.getGold();
        long managerGoldBefore = manager.getGold();
        int agencyItemsBefore = agencyItemRepository.findForUpdate(DAWNWATCH, IRON_INGOT).orElseThrow().getQuantity();
        int managerItemsBefore = managerItemRepository.findForUpdate(MANAGER_4, IRON_INGOT).orElseThrow().getQuantity();

        agency.decreaseGold(100);
        orderRepository.persist(new MarketOrder(agency, item, MarketOrderSide.BUY, 1, 100));

        var result = service.createOrder(MarketOwnerType.MANAGER, null, MarketOrderSide.SELL, IRON_INGOT, 1, 80);

        assertEquals("FILLED", result.status());
        assertEquals(managerGoldBefore + 90, manager.getGold());
        assertEquals(agencyGoldBefore - 100, agency.getGold());
        assertEquals(agencyItemsBefore + 1,
                agencyItemRepository.findForUpdate(DAWNWATCH, IRON_INGOT).orElseThrow().getQuantity());
        assertEquals(managerItemsBefore - 1,
                managerItemRepository.findForUpdate(MANAGER_4, IRON_INGOT).orElseThrow().getQuantity());
    }

    @Test
    @TestTransaction
    void cancellationReturnsOnlyThePersonalOwnersReservations() {
        Manager manager = managerRepository.findById(MANAGER_4);
        long goldBefore = manager.getGold();
        int itemsBefore = managerItemRepository.findForUpdate(MANAGER_4, IRON_INGOT).orElseThrow().getQuantity();

        var buy = service.createOrder(MarketOwnerType.MANAGER, null, MarketOrderSide.BUY, IRON_INGOT, 1, 10);
        var sell = service.createOrder(MarketOwnerType.MANAGER, null, MarketOrderSide.SELL, IRON_INGOT, 1, 9);
        assertEquals("OPEN", buy.status());
        assertEquals("OPEN", sell.status());
        assertEquals(goldBefore - 10, manager.getGold());
        assertEquals(itemsBefore - 1,
                managerItemRepository.findForUpdate(MANAGER_4, IRON_INGOT).orElseThrow().getQuantity());

        assertEquals("CANCELLED", service.cancelOrder(buy.id()).status());
        assertEquals("CANCELLED", service.cancelOrder(sell.id()).status());
        assertEquals(goldBefore, manager.getGold());
        assertEquals(itemsBefore,
                managerItemRepository.findForUpdate(MANAGER_4, IRON_INGOT).orElseThrow().getQuantity());
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000103")
    void anotherManagerCannotCancelPersonalOrder() {
        Manager owner = managerRepository.findById(MANAGER_4);
        owner.decreaseGold(1);
        MarketOrder order = new MarketOrder(owner, itemRepository.findById(MAGIC_CRYSTAL), MarketOrderSide.BUY, 1, 1);
        orderRepository.persistAndFlush(order);

        assertThrows(MarketOrderOwnerRequiredException.class, () -> service.cancelOrder(order.getId()));
    }
}
