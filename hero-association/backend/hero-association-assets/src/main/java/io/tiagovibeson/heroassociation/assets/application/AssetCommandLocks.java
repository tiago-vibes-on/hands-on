package io.tiagovibeson.heroassociation.assets.application;

import java.util.Arrays;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** Serializes command keys across Core replicas, including keys with no row yet. */
@ApplicationScoped
public class AssetCommandLocks {
    @Inject EntityManager entityManager;

    public void lock(UUID... keys) {
        Arrays.stream(keys).mapToLong(key -> key.getMostSignificantBits() ^ key.getLeastSignificantBits())
                .distinct().sorted().forEach(key -> entityManager
                        .createNativeQuery("select 1 from pg_advisory_xact_lock(:key)", Integer.class)
                        .setParameter("key", key).getSingleResult());
    }
}
