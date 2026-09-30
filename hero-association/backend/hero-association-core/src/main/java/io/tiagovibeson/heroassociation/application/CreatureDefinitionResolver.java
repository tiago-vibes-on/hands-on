package io.tiagovibeson.heroassociation.application;

import io.tiagovibeson.heroassociation.domain.CreatureCombatProfile;
import io.tiagovibeson.heroassociation.repository.CreatureDefinitionRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CreatureDefinitionResolver {

    private final CreatureDefinitionRepository repository;
    private final CreatureDefinitionCache cache;

    public CreatureDefinitionResolver(CreatureDefinitionRepository repository, CreatureDefinitionCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    public CreatureCombatProfile resolveLatest(String name) {
        return cache.findLatest(name).orElseGet(() -> {
            CreatureCombatProfile profile = repository.findLatestByName(name)
                    .map(CreatureCombatProfile::from)
                    .orElseThrow(() -> new IllegalStateException("Missing creature definition: " + name));
            cache.putLatest(profile);
            return profile;
        });
    }
}
