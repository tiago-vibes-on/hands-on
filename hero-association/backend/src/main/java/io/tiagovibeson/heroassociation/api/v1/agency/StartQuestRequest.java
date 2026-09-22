package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record StartQuestRequest(@NotNull UUID partyId) {
}
