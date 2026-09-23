package io.tiagovibeson.heroassociation.api.v1.agency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePartyRequest(@NotBlank @Size(max = 100) String name) {
}
