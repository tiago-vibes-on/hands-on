package io.tiagovibeson.heroassociation.api.v1.market;

import java.time.Instant;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.MarketOrder;

public record MarketOrderResponse(
        UUID id,
        UUID agencyId,
        String agencyName,
        UUID itemId,
        String itemCode,
        String itemName,
        String itemSymbol,
        String side,
        int quantityRemaining,
        long priceGoldPerItem,
        Instant createdAt) {

    public static MarketOrderResponse from(MarketOrder order) {
        return new MarketOrderResponse(
                order.getId(),
                order.getAgency().getId(),
                order.getAgency().getName(),
                order.getItem().getId(),
                order.getItem().getCode(),
                order.getItem().getName(),
                order.getItem().getSymbol(),
                order.getSide().name(),
                order.getQuantityRemaining(),
                order.getPriceGoldPerItem(),
                order.getCreatedAt());
    }
}
