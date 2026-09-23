package io.tiagovibeson.heroassociation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.MarketOrder;
import io.tiagovibeson.heroassociation.domain.MarketOrderSide;
import io.tiagovibeson.heroassociation.domain.MarketOrderStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class MarketOrderRepository implements PanacheRepositoryBase<MarketOrder, UUID> {

    public List<MarketOrder> listOpen() {
        return find("status = ?1 or status = ?2 order by createdAt asc, id asc", MarketOrderStatus.OPEN, MarketOrderStatus.PARTIALLY_FILLED)
                .list();
    }

    public List<MarketOrder> listMatchingBuysForUpdate(UUID itemId, UUID sellerAgencyId, long maximumPrice) {
        return find(
                "item.id = ?1 and agency.id <> ?2 and side = ?3 and (status = ?4 or status = ?5) and priceGoldPerItem >= ?6 order by priceGoldPerItem desc, createdAt asc, id asc",
                itemId,
                sellerAgencyId,
                MarketOrderSide.BUY,
                MarketOrderStatus.OPEN,
                MarketOrderStatus.PARTIALLY_FILLED,
                maximumPrice)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .list();
    }

    public List<MarketOrder> listMatchingSellsForUpdate(UUID itemId, UUID buyerAgencyId, long minimumPrice) {
        return find(
                "item.id = ?1 and agency.id <> ?2 and side = ?3 and (status = ?4 or status = ?5) and priceGoldPerItem <= ?6 order by priceGoldPerItem asc, createdAt asc, id asc",
                itemId,
                buyerAgencyId,
                MarketOrderSide.SELL,
                MarketOrderStatus.OPEN,
                MarketOrderStatus.PARTIALLY_FILLED,
                minimumPrice)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .list();
    }

    public Optional<MarketOrder> findForUpdate(UUID orderId, UUID agencyId) {
        return find("id = ?1 and agency.id = ?2", orderId, agencyId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }
}
