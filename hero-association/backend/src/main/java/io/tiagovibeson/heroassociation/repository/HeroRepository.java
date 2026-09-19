package io.tiagovibeson.heroassociation.repository;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.tiagovibeson.heroassociation.domain.Hero;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class HeroRepository implements PanacheRepository<Hero> {

    public Optional<Hero> findByAlias(String alias) {
        return find("alias", alias).firstResultOptional();
    }
}
