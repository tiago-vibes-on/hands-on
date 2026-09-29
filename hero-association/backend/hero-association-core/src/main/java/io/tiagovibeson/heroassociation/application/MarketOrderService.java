package io.tiagovibeson.heroassociation.application;

import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.market.MarketOrderResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.ManagerOnboardingRequiredException;
import io.tiagovibeson.heroassociation.application.exception.MarketOrderNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.MarketOrderOwnerRequiredException;
import io.tiagovibeson.heroassociation.application.exception.MarketOrderRejectedException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.AgencyItem;
import io.tiagovibeson.heroassociation.domain.Item;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.domain.ManagerItem;
import io.tiagovibeson.heroassociation.domain.MarketOrder;
import io.tiagovibeson.heroassociation.domain.MarketOrderSide;
import io.tiagovibeson.heroassociation.domain.MarketOwnerType;
import io.tiagovibeson.heroassociation.repository.AgencyItemRepository;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.ItemRepository;
import io.tiagovibeson.heroassociation.repository.ManagerItemRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRepository;
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
    ManagerRepository managerRepository;

    @Inject
    ManagerItemRepository managerItemRepository;

    @Inject
    ItemRepository itemRepository;

    @Inject
    MarketOrderRepository marketOrderRepository;

    @Inject
    AgencyAccessService agencyAccessService;

    @Transactional
    public MarketOrderResponse createOrder(
            MarketOwnerType ownerType,
            UUID agencyId,
            MarketOrderSide side,
            UUID itemId,
            int quantity,
            long priceGoldPerItem) {
        Item item = itemRepository.findByIdOptional(itemId)
                .orElseThrow(() -> new MarketOrderRejectedException("The requested item does not exist."));

        MarketOrder order;
        if (ownerType == MarketOwnerType.AGENCY) {
            if (agencyId == null) {
                throw new MarketOrderRejectedException("An agency order requires agencyId.");
            }
            Agency agency = findAgencyForUpdate(agencyId);
            agencyAccessService.requireLeadership(agencyId);
            order = new MarketOrder(agency, item, side, quantity, priceGoldPerItem);
        } else if (ownerType == MarketOwnerType.MANAGER) {
            if (agencyId != null) {
                throw new MarketOrderRejectedException("A personal order must not include agencyId.");
            }
            Manager currentManager = agencyAccessService.currentManager();
            order = new MarketOrder(findManagerForUpdate(currentManager.getId()), item, side, quantity, priceGoldPerItem);
        } else {
            throw new MarketOrderRejectedException("A market order owner is required.");
        }

        reserveOrderResources(order);
        marketOrderRepository.persist(order);
        match(order);
        return MarketOrderResponse.from(order);
    }

    @Transactional
    public MarketOrderResponse cancelOrder(UUID orderId) {
        MarketOrder existingOrder = marketOrderRepository.findByIdOptional(orderId)
                .orElseThrow(() -> new MarketOrderNotFoundException(orderId));
        if (existingOrder.getOwnerType() == MarketOwnerType.AGENCY) {
            UUID agencyId = existingOrder.getOwnerId();
            agencyAccessService.requireLeadership(agencyId);
            findAgencyForUpdate(agencyId);
        } else {
            Manager currentManager = agencyAccessService.currentManager();
            if (!currentManager.getId().equals(existingOrder.getOwnerId())) {
                throw new MarketOrderOwnerRequiredException();
            }
            findManagerForUpdate(currentManager.getId());
        }

        MarketOrder order = marketOrderRepository.findForUpdate(orderId)
                .orElseThrow(() -> new MarketOrderNotFoundException(orderId));
        if (order.getQuantityRemaining() == 0) {
            throw new MarketOrderRejectedException("A filled market order cannot be cancelled.");
        }
        if (order.getSide() == MarketOrderSide.BUY) {
            creditGold(order, orderValue(order.getPriceGoldPerItem(), order.getQuantityRemaining()));
        } else {
            creditItem(order, order.getItem(), order.getQuantityRemaining());
        }
        order.cancel();
        return MarketOrderResponse.from(order);
    }

    @Transactional
    public List<MarketOrderResponse> listOpenOrders() {
        return marketOrderRepository.listOpen().stream()
                .map(MarketOrderResponse::from)
                .toList();
    }

    private void reserveOrderResources(MarketOrder order) {
        if (order.getSide() == MarketOrderSide.BUY) {
            debitGold(order, orderValue(order.getPriceGoldPerItem(), order.getQuantityRemaining()));
            return;
        }

        int quantity = order.getQuantityRemaining();
        Item item = order.getItem();
        if (order.getOwnerType() == MarketOwnerType.AGENCY) {
            AgencyItem agencyItem = agencyItemRepository.findForUpdate(order.getOwnerId(), item.getId())
                    .orElseThrow(() -> new MarketOrderRejectedException("The requested item is not in this agency inventory."));
            if (agencyItem.getQuantity() < quantity) {
                throw new MarketOrderRejectedException("The agency does not have enough of this item for this sell order.");
            }
            agencyItem.decreaseQuantity(quantity);
        } else {
            ManagerItem managerItem = managerItemRepository.findForUpdate(order.getOwnerId(), item.getId())
                    .orElseThrow(() -> new MarketOrderRejectedException("The requested item is not in this Manager inventory."));
            if (managerItem.getQuantity() < quantity) {
                throw new MarketOrderRejectedException("The Manager does not have enough of this item for this sell order.");
            }
            managerItem.decreaseQuantity(quantity);
        }
    }

    private void match(MarketOrder incomingOrder) {
        List<MarketOrder> matchingOrders = incomingOrder.getSide() == MarketOrderSide.BUY
                ? marketOrderRepository.listMatchingSellsForUpdate(
                        incomingOrder.getItem().getId(), incomingOrder.getPriceGoldPerItem())
                : marketOrderRepository.listMatchingBuysForUpdate(
                        incomingOrder.getItem().getId(), incomingOrder.getPriceGoldPerItem());

        for (MarketOrder restingOrder : matchingOrders) {
            if (incomingOrder.getQuantityRemaining() == 0) {
                return;
            }
            if (!incomingOrder.hasSameOwner(restingOrder)) {
                executeTrade(incomingOrder, restingOrder);
            }
        }
    }

    private void executeTrade(MarketOrder incomingOrder, MarketOrder restingOrder) {
        MarketOrder buyerOrder = incomingOrder.getSide() == MarketOrderSide.BUY ? incomingOrder : restingOrder;
        MarketOrder sellerOrder = incomingOrder.getSide() == MarketOrderSide.SELL ? incomingOrder : restingOrder;
        int quantity = Math.min(buyerOrder.getQuantityRemaining(), sellerOrder.getQuantityRemaining());
        long executionPrice = restingOrder.getPriceGoldPerItem();
        long grossGold = orderValue(executionPrice, quantity);
        long fee = grossGold / (100 / MARKET_FEE_PERCENT);

        if (buyerOrder.getPriceGoldPerItem() > executionPrice) {
            creditGold(buyerOrder, orderValue(buyerOrder.getPriceGoldPerItem() - executionPrice, quantity));
        }
        creditGold(sellerOrder, grossGold - fee);
        creditItem(buyerOrder, buyerOrder.getItem(), quantity);

        buyerOrder.fill(quantity);
        sellerOrder.fill(quantity);
    }

    private void debitGold(MarketOrder owner, long amount) {
        if (owner.getOwnerType() == MarketOwnerType.AGENCY) {
            Agency agency = findAgencyForUpdate(owner.getOwnerId());
            if (agency.getGold() < amount) {
                throw new MarketOrderRejectedException("The agency does not have enough gold for this buy order.");
            }
            agency.decreaseGold(amount);
        } else {
            Manager manager = findManagerForUpdate(owner.getOwnerId());
            if (manager.getGold() < amount) {
                throw new MarketOrderRejectedException("The Manager does not have enough gold for this buy order.");
            }
            manager.decreaseGold(amount);
        }
    }

    private void creditGold(MarketOrder owner, long amount) {
        if (owner.getOwnerType() == MarketOwnerType.AGENCY) {
            findAgencyForUpdate(owner.getOwnerId()).increaseGold(amount);
        } else {
            findManagerForUpdate(owner.getOwnerId()).increaseGold(amount);
        }
    }

    private void creditItem(MarketOrder owner, Item item, int quantity) {
        if (owner.getOwnerType() == MarketOwnerType.AGENCY) {
            Agency agency = findAgencyForUpdate(owner.getOwnerId());
            AgencyItem agencyItem = agencyItemRepository.findForUpdate(agency.getId(), item.getId()).orElse(null);
            if (agencyItem == null) {
                agencyItemRepository.persist(new AgencyItem(agency, item, quantity));
            } else {
                agencyItem.increaseQuantity(quantity);
            }
        } else {
            Manager manager = findManagerForUpdate(owner.getOwnerId());
            ManagerItem managerItem = managerItemRepository.findForUpdate(manager.getId(), item.getId()).orElse(null);
            if (managerItem == null) {
                managerItemRepository.persist(new ManagerItem(manager, item, quantity));
            } else {
                managerItem.increaseQuantity(quantity);
            }
        }
    }

    private Agency findAgencyForUpdate(UUID agencyId) {
        return agencyRepository.findForUpdate(agencyId)
                .orElseThrow(() -> new AgencyNotFoundException(agencyId));
    }

    private Manager findManagerForUpdate(UUID managerId) {
        return managerRepository.findForUpdate(managerId)
                .orElseThrow(ManagerOnboardingRequiredException::new);
    }

    private long orderValue(long priceGoldPerItem, int quantity) {
        try {
            return Math.multiplyExact(priceGoldPerItem, quantity);
        } catch (ArithmeticException exception) {
            throw new MarketOrderRejectedException("The market order value is too large.");
        }
    }
}
