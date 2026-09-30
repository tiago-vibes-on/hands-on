package io.tiagovibeson.heroassociation.application;

import java.util.Optional;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.tiagovibeson.heroassociation.domain.CreatureCombatProfile;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CreatureDefinitionCache {

    private static final Logger LOG = Logger.getLogger(CreatureDefinitionCache.class);
    private static final long TTL_SECONDS = 60;
    private static final String KEY_PREFIX = "hero-association:creature-definition:v1:";

    private final ValueCommands<String, CreatureCombatProfile> commands;

    public CreatureDefinitionCache(RedisDataSource redisDataSource) {
        commands = redisDataSource.value(CreatureCombatProfile.class);
    }

    public Optional<CreatureCombatProfile> findLatest(String name) {
        try {
            CreatureCombatProfile cached = commands.get(keyFor(name));
            return cached != null && name.equals(cached.name()) ? Optional.of(cached) : Optional.empty();
        } catch (RuntimeException exception) {
            LOG.warnf("Creature cache read failed for %s; using PostgreSQL (%s)", name, exception.toString());
            return Optional.empty();
        }
    }

    public void putLatest(CreatureCombatProfile profile) {
        try {
            commands.setex(keyFor(profile.name()), TTL_SECONDS, profile);
        } catch (RuntimeException exception) {
            LOG.warnf("Creature cache write failed for %s; battle will use PostgreSQL data (%s)",
                    profile.name(), exception.toString());
        }
    }

    static String keyFor(String name) {
        return KEY_PREFIX + name;
    }
}
