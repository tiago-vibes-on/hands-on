package io.tiagovibeson.heroassociation.repository;

import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.Quest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class QuestRepository implements PanacheRepositoryBase<Quest, UUID> {

    public Optional<Quest> findForStart(UUID agencyId, UUID questId) {
        return find("id = ?1 and agency.id = ?2", questId, agencyId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }
}
