package io.tiagovibeson.heroassociation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.QuestCombat;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class QuestCombatRepository implements PanacheRepositoryBase<QuestCombat, UUID> {

    public Optional<QuestCombat> findForUpdate(UUID agencyId, UUID questId) {
        return find("quest.id = ?1 and quest.agency.id = ?2", questId, agencyId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }

    public List<QuestCombat> listInProgressForUpdate() {
        return find("status", CombatStatus.IN_PROGRESS)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .list();
    }
}
