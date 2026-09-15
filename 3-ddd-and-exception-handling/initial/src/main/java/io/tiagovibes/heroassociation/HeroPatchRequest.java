package io.tiagovibes.heroassociation;

import jakarta.validation.constraints.Size;

public record HeroPatchRequest(
        @Size(min = 1, max = 100) String name,
        @Size(min = 1, max = 100) String alias,
        @Size(min = 1, max = 255) String power) {

    public boolean isEmpty() {
        return name == null && alias == null && power == null;
    }
}
