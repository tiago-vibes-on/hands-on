package io.tiagovibeson.heroassociation.repository;

import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.CreatureDefinition;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CreatureDefinitionRepository implements PanacheRepositoryBase<CreatureDefinition, UUID> {
    public Optional<CreatureDefinition> findLatestByName(String name) {
        return find("name = ?1 order by version desc", name).firstResultOptional();
    }
}
