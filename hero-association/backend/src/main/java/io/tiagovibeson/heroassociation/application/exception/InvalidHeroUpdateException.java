package io.tiagovibeson.heroassociation.application.exception;

public class InvalidHeroUpdateException extends HeroApplicationException {

    public InvalidHeroUpdateException() {
        super("At least one field must be provided.");
    }
}
