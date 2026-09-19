package io.tiagovibeson.heroassociation.application;

import java.util.List;

import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class HeroApplicationService {

    @Inject
    HeroRepository heroRepository;

    @Transactional
    public Hero register(String name, String alias, String power) {
        ensureAliasIsAvailable(alias, null);

        Hero hero = Hero.register(name, alias, power);
        heroRepository.persist(hero);
        return hero;
    }

    public List<Hero> findAll() {
        return heroRepository.listAll();
    }

    public Hero findById(Long id) {
        return heroRepository.findByIdOptional(id)
                .orElseThrow(() -> new HeroNotFoundException(id));
    }

    @Transactional
    public Hero replace(Long id, String name, String alias, String power) {
        Hero hero = findById(id);
        ensureAliasIsAvailable(alias, hero.getId());
        hero.replace(name, alias, power);
        return hero;
    }

    @Transactional
    public Hero update(Long id, String name, String alias, String power) {
        Hero hero = findById(id);
        if (alias != null) {
            ensureAliasIsAvailable(alias, hero.getId());
        }
        hero.update(name, alias, power);
        return hero;
    }

    @Transactional
    public void delete(Long id) {
        heroRepository.delete(findById(id));
    }

    private void ensureAliasIsAvailable(String alias, Long currentHeroId) {
        boolean exists = heroRepository.findByAlias(alias)
                .filter(hero -> !hero.getId().equals(currentHeroId))
                .isPresent();

        if (exists) {
            throw new AliasAlreadyRegisteredException(alias);
        }
    }
}
