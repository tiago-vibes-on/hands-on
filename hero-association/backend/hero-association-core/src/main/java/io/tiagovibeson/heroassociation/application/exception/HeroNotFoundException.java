package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class HeroNotFoundException extends RuntimeException {

    public HeroNotFoundException(UUID heroId) {
        super("Hero with id %s was not found in this agency.".formatted(heroId));
    }
}
