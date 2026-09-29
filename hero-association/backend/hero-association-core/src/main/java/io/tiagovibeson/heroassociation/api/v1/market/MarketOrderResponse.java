package io.tiagovibeson.heroassociation.api.v1.market;

import java.time.Instant;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.MarketOrder;

public record MarketOrderResponse(
        UUID id,
        String ownerType,
        UUID ownerId,
        String ownerName,
        UUID itemId,
        String itemCode,
        String itemName,
        String itemSymbol,
        String side,
        String status,
        int quantityRemaining,
        long priceGoldPerItem,
        Instant createdAt) {

    public static MarketOrderResponse from(MarketOrder order) {
        return new MarketOrderResponse(
                order.getId(),
                order.getOwnerType().name(),
                order.getOwnerId(),
                order.getOwnerName(),
                order.getItem().getId(),
                order.getItem().getCode(),
                order.getItem().getName(),
                order.getItem().getSymbol(),
                order.getSide().name(),
                order.getStatus().name(),
                order.getQuantityRemaining(),
                order.getPriceGoldPerItem(),
                order.getCreatedAt());
    }
}
