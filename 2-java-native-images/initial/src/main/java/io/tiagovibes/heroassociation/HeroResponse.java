package io.tiagovibes.heroassociation;

public record HeroResponse(Long id, String name, String alias, String power) {

    public static HeroResponse from(Hero hero) {
        return new HeroResponse(hero.id, hero.name, hero.alias, hero.power);
    }
}
