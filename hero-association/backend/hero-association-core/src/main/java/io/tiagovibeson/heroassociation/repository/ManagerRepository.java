package io.tiagovibeson.heroassociation.repository;

import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.Manager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class ManagerRepository implements PanacheRepositoryBase<Manager, UUID> {

    public Optional<Manager> findByAccountId(UUID accountId) {
        return find("account.id", accountId).firstResultOptional();
    }

    public Optional<Manager> findForUpdate(UUID managerId) {
        return find("id", managerId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }

    public boolean existsWithNormalizedDisplayName(String displayNameNormalized) {
        return count("displayNameNormalized", displayNameNormalized) > 0;
    }
}
