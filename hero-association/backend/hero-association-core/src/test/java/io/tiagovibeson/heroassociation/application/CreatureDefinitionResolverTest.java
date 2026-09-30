package io.tiagovibeson.heroassociation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.domain.CreatureCombatProfile;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CreatureDefinitionResolverTest {

    @Inject
    CreatureDefinitionResolver resolver;

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    EntityManager entityManager;

    @Test
    @TestTransaction
    void shouldReuseCachedProfileWithinItsTtl() {
        UUID id = UuidV7.next();
        String name = "Cache Probe " + id;
        insertDefinition(id, name);

        CreatureCombatProfile first = resolver.resolveLatest(name);
        assertEquals(111, first.maxHealth());
        assertEquals(id, first.definitionId());

        entityManager.createNativeQuery("UPDATE creature_definition SET max_health = 222 WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();

        assertEquals(111, resolver.resolveLatest(name).maxHealth());
    }

    @Test
    @TestTransaction
    void shouldFallBackToPostgresWhenCachedValueIsInvalid() {
        UUID id = UuidV7.next();
        String name = "Corrupt Cache Probe " + id;
        insertDefinition(id, name);
        redisDataSource.value(String.class).set(CreatureDefinitionCache.keyFor(name), "not-a-profile");

        CreatureCombatProfile recovered = resolver.resolveLatest(name);

        assertEquals(id, recovered.definitionId());
        assertEquals(111, recovered.maxHealth());
    }

    private void insertDefinition(UUID id, String name) {
        entityManager.createNativeQuery("""
                INSERT INTO creature_definition (
                    id, name, version, base_experience, max_health, max_mana,
                    attack_damage, attack_interval_milliseconds,
                    health_recovery_per_second, mana_recovery_per_second,
                    critical_chance, critical_damage_multiplier)
                VALUES (:id, :name, 1, 100, 111, 100, 10, 1600, 0, 0, 0, 2)
                """)
                .setParameter("id", id)
                .setParameter("name", name)
                .executeUpdate();
    }
}
