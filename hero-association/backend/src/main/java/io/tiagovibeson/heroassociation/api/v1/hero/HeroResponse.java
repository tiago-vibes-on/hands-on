package io.tiagovibeson.heroassociation.api.v1.hero;

import io.tiagovibeson.heroassociation.domain.Hero;

public record HeroResponse(Long id, String name, String alias, String power) {

    public static HeroResponse from(Hero hero) {
        return new HeroResponse(hero.getId(), hero.getName(), hero.getAlias(), hero.getPower());
    }
}
