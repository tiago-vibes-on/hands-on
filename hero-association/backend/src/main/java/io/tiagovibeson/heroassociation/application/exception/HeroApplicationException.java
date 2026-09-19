package io.tiagovibeson.heroassociation.application.exception;

public abstract class HeroApplicationException extends RuntimeException {

    protected HeroApplicationException(String message) {
        super(message);
    }
}
