package io.tiagovibeson.heroassociation.repository;

import java.util.List;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class HeroRepository implements PanacheRepositoryBase<Hero, UUID> {

    public List<Hero> listRecruitable() {
        return list("agency is null order by alias");
    }

    public java.util.Optional<Hero> findForUpdate(UUID heroId) {
        return find("id", heroId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }

    public List<Hero> listRecoveringForUpdate() {
        return find("activity = ?1 or activity = ?2", HeroActivity.TRAINING, HeroActivity.RESTING)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .list();
    }
}
