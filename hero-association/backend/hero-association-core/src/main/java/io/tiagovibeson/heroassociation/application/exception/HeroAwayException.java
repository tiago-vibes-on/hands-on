package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class HeroAwayException extends RuntimeException {

    public HeroAwayException(UUID heroId) {
        super("Hero with id %s is on an expedition and is not available for this action.".formatted(heroId));
    }
}
