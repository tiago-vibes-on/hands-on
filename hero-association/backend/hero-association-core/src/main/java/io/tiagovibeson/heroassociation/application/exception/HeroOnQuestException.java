package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class HeroOnQuestException extends RuntimeException {

    public HeroOnQuestException(UUID heroId) {
        super("Hero with id %s is on a quest and is not available for this action.".formatted(heroId));
    }
}
