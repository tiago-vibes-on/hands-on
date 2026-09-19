package io.tiagovibeson.heroassociation.api.v1.hero;

import jakarta.validation.constraints.Size;

public record PatchHeroRequest(
        @Size(min = 1, max = 100) String name,
        @Size(min = 1, max = 100) String alias,
        @Size(min = 1, max = 255) String power) {
}
