package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class HeroAlreadyAssignedException extends RuntimeException {

    public HeroAlreadyAssignedException(UUID heroId) {
        super("Hero with id " + heroId + " is already assigned to another party.");
    }
}
