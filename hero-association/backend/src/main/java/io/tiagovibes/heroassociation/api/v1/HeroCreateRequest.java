package io.tiagovibes.heroassociation.api.v1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HeroCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String alias,
        @NotBlank @Size(max = 255) String power) {
}
