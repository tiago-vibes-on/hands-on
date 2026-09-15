package io.tiagovibes.heroassociation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HeroRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String alias,
        @NotBlank @Size(max = 255) String power) {
}
