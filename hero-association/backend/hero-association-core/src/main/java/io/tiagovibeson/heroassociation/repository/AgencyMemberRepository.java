package io.tiagovibeson.heroassociation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.AgencyMember;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AgencyMemberRepository implements PanacheRepositoryBase<AgencyMember, UUID> {

    public Optional<AgencyMember> findByAgencyAndManagerId(UUID agencyId, UUID managerId) {
        return find("agency.id = ?1 and manager.id = ?2", agencyId, managerId).firstResultOptional();
    }

    public List<AgencyMember> listByManagerId(UUID managerId) {
        return list("manager.id = ?1 order by agency.name", managerId);
    }
}
