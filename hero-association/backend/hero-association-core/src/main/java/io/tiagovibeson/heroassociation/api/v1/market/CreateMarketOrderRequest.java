package io.tiagovibeson.heroassociation.api.v1.market;

import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.MarketOrderSide;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateMarketOrderRequest(
        @NotNull MarketOrderSide side,
        @NotNull UUID itemId,
        @Positive int quantity,
        @Positive long priceGoldPerItem) {
}
