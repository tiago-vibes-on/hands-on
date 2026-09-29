package io.tiagovibeson.heroassociation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.ManagerItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class ManagerItemRepository implements PanacheRepositoryBase<ManagerItem, UUID> {

    public List<ManagerItem> listByManagerId(UUID managerId) {
        return list("manager.id = ?1 order by item.code", managerId);
    }

    public Optional<ManagerItem> findForUpdate(UUID managerId, UUID itemId) {
        return find("manager.id = ?1 and item.id = ?2", managerId, itemId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }
}
