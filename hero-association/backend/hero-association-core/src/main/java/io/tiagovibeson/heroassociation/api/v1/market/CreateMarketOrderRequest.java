package io.tiagovibeson.heroassociation.api.v1.market;

import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.MarketOrderSide;
import io.tiagovibeson.heroassociation.domain.MarketOwnerType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateMarketOrderRequest(
        @NotNull MarketOwnerType ownerType,
        UUID agencyId,
        @NotNull MarketOrderSide side,
        @NotNull UUID itemId,
        @Positive int quantity,
        @Positive long priceGoldPerItem) {
}
