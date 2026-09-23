package io.tiagovibeson.heroassociation.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void shouldCreateRfc9562VersionSevenUuids() {
        long beforeGeneration = System.currentTimeMillis();

        UUID uuid = UuidV7.next();

        long encodedTimestamp = uuid.getMostSignificantBits() >>> 16;
        assertThat(uuid.version(), is(7));
        assertThat(uuid.variant(), is(2));
        assertThat(beforeGeneration, lessThanOrEqualTo(encodedTimestamp));
        assertThat(encodedTimestamp, lessThanOrEqualTo(System.currentTimeMillis()));
    }
}
