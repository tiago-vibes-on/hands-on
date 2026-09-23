package io.tiagovibeson.heroassociation.repository;

import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.AgencyItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class AgencyItemRepository implements PanacheRepositoryBase<AgencyItem, UUID> {

    public Optional<AgencyItem> findForUpdate(UUID agencyId, UUID itemId) {
        return find("agency.id = ?1 and item.id = ?2", agencyId, itemId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }
}
