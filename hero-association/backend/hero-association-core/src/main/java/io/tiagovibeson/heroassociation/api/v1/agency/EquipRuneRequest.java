package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.RuneInventoryOwnerType;
import jakarta.validation.constraints.NotNull;

public record EquipRuneRequest(@NotNull UUID operationKey, @NotNull UUID runeId, @NotNull RuneInventoryOwnerType sourceOwnerType) {
}
