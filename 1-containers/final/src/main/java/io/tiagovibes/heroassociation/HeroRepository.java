package io.tiagovibes.heroassociation;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class HeroRepository implements PanacheRepository<Hero> {

    public Optional<Hero> findByAlias(String alias) {
        return find("alias", alias).firstResultOptional();
    }
}
