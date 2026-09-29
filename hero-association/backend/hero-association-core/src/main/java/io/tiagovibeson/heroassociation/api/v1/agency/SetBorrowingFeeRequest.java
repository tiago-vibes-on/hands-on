package io.tiagovibeson.heroassociation.api.v1.agency;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SetBorrowingFeeRequest(@NotNull @PositiveOrZero Long feeGold) {
}
