package io.tiagovibeson.heroassociation.repository;

import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.Party;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class PartyRepository implements PanacheRepositoryBase<Party, UUID> {

    public Optional<Party> findOwnedForUpdate(UUID agencyId, UUID partyId, UUID managerId) {
        return find("id = ?1 and agency.id = ?2 and ownerManager.id = ?3",
                partyId, agencyId, managerId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }
}
