package io.tiagovibeson.heroassociation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(CreatureCacheOutageTest.RedisUnavailableProfile.class)
class CreatureCacheOutageTest {

    @Inject
    CreatureDefinitionResolver resolver;

    @Test
    void shouldReadPostgresWhenRedisIsUnavailable() {
        assertEquals(2000, resolver.resolveLatest("Troll").maxHealth());
    }

    public static class RedisUnavailableProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.redis.hosts", "redis://127.0.0.1:1",
                    "quarkus.redis.devservices.enabled", "false");
        }
    }
}
