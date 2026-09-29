package io.tiagovibeson.heroassociation.api.v1.gold;

import java.math.BigDecimal;

import io.tiagovibeson.heroassociation.domain.GoldTransferDirection;

public record GoldTransferRequest(
        GoldTransferDirection direction,
        String agencyName,
        String managerName,
        BigDecimal amountGold) {

    public GoldTransferRequest(GoldTransferDirection direction, String agencyName, String managerName, long amountGold) {
        this(direction, agencyName, managerName, BigDecimal.valueOf(amountGold));
    }
}
