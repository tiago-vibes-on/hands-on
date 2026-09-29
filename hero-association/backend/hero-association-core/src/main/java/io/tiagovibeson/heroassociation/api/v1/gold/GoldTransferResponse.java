package io.tiagovibeson.heroassociation.api.v1.gold;

import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.GoldTransferDirection;
import io.tiagovibeson.heroassociation.domain.Manager;

public record GoldTransferResponse(
        GoldTransferDirection direction,
        long amountGold,
        UUID agencyId,
        String agencyName,
        long agencyGold,
        UUID managerId,
        String managerName,
        long managerGold) {

    public static GoldTransferResponse from(GoldTransferDirection direction, long amountGold, Agency agency, Manager manager) {
        return new GoldTransferResponse(
                direction, amountGold,
                agency.getId(), agency.getName(), agency.getGold(),
                manager.getId(), manager.getDisplayName(), manager.getGold());
    }
}
