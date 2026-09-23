package io.tiagovibeson.heroassociation.application;

import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.api.v1.market.MarketOrderResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.MarketOrderNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.MarketOrderRejectedException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.AgencyItem;
import io.tiagovibeson.heroassociation.domain.Item;
import io.tiagovibeson.heroassociation.domain.MarketOrder;
import io.tiagovibeson.heroassociation.domain.MarketOrderSide;
import io.tiagovibeson.heroassociation.repository.AgencyItemRepository;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.ItemRepository;
import io.tiagovibeson.heroassociation.repository.MarketOrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class MarketOrderService {

    private static final int MARKET_FEE_PERCENT = 10;

    @Inject
    AgencyRepository agencyRepository;

    @Inject
    AgencyItemRepository agencyItemRepository;

    @Inject
    ItemRepository itemRepository;

    @Inject
    MarketOrderRepository marketOrderRepository;

    @Inject
    AgencyStateService agencyStateService;

    @Inject
    AgencyAccessService agencyAccessService;

    @Transactional
    public AgencyStateResponse createOrder(
            UUID agencyId,
            MarketOrderSide side,
            UUID itemId,
            int quantity,
            long priceGoldPerItem) {
        Agency agency = findAgencyForUpdate(agencyId);
        agencyAccessService.requireLeadership(agencyId);
        Item item = itemRepository.findByIdOptional(itemId)
                .orElseThrow(() -> new MarketOrderRejectedException("The requested item does not exist."));

        reserveOrderResources(agency, item, side, quantity, priceGoldPerItem);
        MarketOrder order = new MarketOrder(agency, item, side, quantity, priceGoldPerItem);
        marketOrderRepository.persist(order);
        match(order);

        return agencyStateService.findState(agencyId);
    }

    @Transactional
    public AgencyStateResponse cancelOrder(UUID agencyId, UUID orderId) {
        Agency agency = findAgencyForUpdate(agencyId);
        agencyAccessService.requireLeadership(agencyId);
        MarketOrder order = marketOrderRepository.findForUpdate(orderId, agencyId)
                .orElseThrow(() -> new MarketOrderNotFoundException(orderId));
        if (order.getQuantityRemaining() == 0) {
            throw new MarketOrderRejectedException("A filled market order cannot be cancelled.");
        }

        if (order.getSide() == MarketOrderSide.BUY) {
            agency.increaseGold(orderValue(order.getPriceGoldPerItem(), order.getQuantityRemaining()));
        } else {
            creditItem(agency, order.getItem(), order.getQuantityRemaining());
        }
        order.cancel();

        return agencyStateService.findState(agencyId);
    }

    @Transactional
    public List<MarketOrderResponse> listOpenOrders() {
        return marketOrderRepository.listOpen().stream()
                .map(MarketOrderResponse::from)
                .toList();
    }

    private void reserveOrderResources(Agency agency, Item item, MarketOrderSide side, int quantity, long priceGoldPerItem) {
        if (side == MarketOrderSide.BUY) {
            long reservedGold = orderValue(priceGoldPerItem, quantity);
            if (agency.getGold() < reservedGold) {
                throw new MarketOrderRejectedException("The agency does not have enough gold for this buy order.");
            }
            agency.decreaseGold(reservedGold);
            return;
        }

        AgencyItem agencyItem = agencyItemRepository.findForUpdate(agency.getId(), item.getId())
                .orElseThrow(() -> new MarketOrderRejectedException("The requested item is not in this agency inventory."));
        if (agencyItem.getQuantity() < quantity) {
            throw new MarketOrderRejectedException("The agency does not have enough of this item for this sell order.");
        }
        agencyItem.decreaseQuantity(quantity);
    }

    private void match(MarketOrder incomingOrder) {
        List<MarketOrder> matchingOrders = incomingOrder.getSide() == MarketOrderSide.BUY
                ? marketOrderRepository.listMatchingSellsForUpdate(
                        incomingOrder.getItem().getId(),
                        incomingOrder.getAgency().getId(),
                        incomingOrder.getPriceGoldPerItem())
                : marketOrderRepository.listMatchingBuysForUpdate(
                        incomingOrder.getItem().getId(),
                        incomingOrder.getAgency().getId(),
                        incomingOrder.getPriceGoldPerItem());

        for (MarketOrder restingOrder : matchingOrders) {
            if (incomingOrder.getQuantityRemaining() == 0) {
                return;
            }
            executeTrade(incomingOrder, restingOrder);
        }
    }

    private void executeTrade(MarketOrder incomingOrder, MarketOrder restingOrder) {
        MarketOrder buyerOrder = incomingOrder.getSide() == MarketOrderSide.BUY ? incomingOrder : restingOrder;
        MarketOrder sellerOrder = incomingOrder.getSide() == MarketOrderSide.SELL ? incomingOrder : restingOrder;
        int quantity = Math.min(buyerOrder.getQuantityRemaining(), sellerOrder.getQuantityRemaining());
        long executionPrice = restingOrder.getPriceGoldPerItem();
        long grossGold = orderValue(executionPrice, quantity);
        long fee = grossGold / (100 / MARKET_FEE_PERCENT);

        Agency buyer = findAgencyForUpdate(buyerOrder.getAgency().getId());
        Agency seller = findAgencyForUpdate(sellerOrder.getAgency().getId());
        if (buyerOrder.getPriceGoldPerItem() > executionPrice) {
            buyer.increaseGold(orderValue(buyerOrder.getPriceGoldPerItem() - executionPrice, quantity));
        }
        seller.increaseGold(grossGold - fee);
        creditItem(buyer, buyerOrder.getItem(), quantity);

        buyerOrder.fill(quantity);
        sellerOrder.fill(quantity);
    }

    private void creditItem(Agency agency, Item item, int quantity) {
        AgencyItem agencyItem = agencyItemRepository.findForUpdate(agency.getId(), item.getId()).orElse(null);
        if (agencyItem == null) {
            agencyItemRepository.persist(new AgencyItem(agency, item, quantity));
            return;
        }

        agencyItem.increaseQuantity(quantity);
    }

    private Agency findAgencyForUpdate(UUID agencyId) {
        return agencyRepository.findForUpdate(agencyId)
                .orElseThrow(() -> new AgencyNotFoundException(agencyId));
    }

    private long orderValue(long priceGoldPerItem, int quantity) {
        try {
            return Math.multiplyExact(priceGoldPerItem, quantity);
        } catch (ArithmeticException exception) {
            throw new MarketOrderRejectedException("The market order value is too large.");
        }
    }
}
