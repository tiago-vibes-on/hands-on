package io.tiagovibeson.heroassociation.repository;

import java.util.List;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.ManagerRune;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ManagerRuneRepository implements PanacheRepositoryBase<ManagerRune, UUID> {

    public List<ManagerRune> listByManagerId(UUID managerId) {
        return list("manager.id = ?1 order by rune.code", managerId);
    }
}
