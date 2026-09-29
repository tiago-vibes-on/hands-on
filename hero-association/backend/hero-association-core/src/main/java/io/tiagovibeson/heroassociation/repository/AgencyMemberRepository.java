package io.tiagovibeson.heroassociation.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.AgencyMember;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AgencyMemberRepository implements PanacheRepositoryBase<AgencyMember, UUID> {

    public boolean existsByManagerId(UUID managerId) {
        return count("manager.id", managerId) > 0;
    }

    public Optional<AgencyMember> findByAgencyAndManagerId(UUID agencyId, UUID managerId) {
        return find("agency.id = ?1 and manager.id = ?2", agencyId, managerId).firstResultOptional();
    }

    public List<AgencyMember> listByManagerId(UUID managerId) {
        return list("manager.id = ?1 order by agency.name", managerId);
    }

    public List<AgencyMember> listByManagerIds(Set<UUID> managerIds) {
        return list("manager.id in ?1", managerIds);
    }
}
