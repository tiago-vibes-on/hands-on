package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StartQuestRequest(@NotNull UUID partyId, @NotNull @PositiveOrZero Long expectedBorrowingFeeGold) {
}
