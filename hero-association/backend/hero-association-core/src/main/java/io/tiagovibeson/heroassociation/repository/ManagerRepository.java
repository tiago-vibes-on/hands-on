package io.tiagovibeson.heroassociation.repository;

import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.Manager;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ManagerRepository implements PanacheRepositoryBase<Manager, UUID> {

    public Optional<Manager> findByAccountId(UUID accountId) {
        return find("account.id", accountId).firstResultOptional();
    }

    public boolean existsWithNormalizedDisplayName(String displayNameNormalized) {
        return count("displayNameNormalized", displayNameNormalized) > 0;
    }
}
