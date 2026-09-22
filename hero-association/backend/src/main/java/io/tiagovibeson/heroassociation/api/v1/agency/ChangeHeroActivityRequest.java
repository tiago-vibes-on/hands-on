package io.tiagovibeson.heroassociation.api.v1.agency;

import io.tiagovibeson.heroassociation.domain.HeroActivity;
import jakarta.validation.constraints.NotNull;

public record ChangeHeroActivityRequest(@NotNull HeroActivity activity) {
}
