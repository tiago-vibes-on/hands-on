package io.tiagovibeson.heroassociation.application;

public class HeroNotFoundException extends HeroApplicationException {

    public HeroNotFoundException(Long id) {
        super("Hero with id " + id + " was not found.");
    }
}
