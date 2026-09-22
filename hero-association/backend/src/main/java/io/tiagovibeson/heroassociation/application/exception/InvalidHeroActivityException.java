package io.tiagovibeson.heroassociation.application.exception;

public class InvalidHeroActivityException extends RuntimeException {

    public InvalidHeroActivityException() {
        super("A hero activity can only be TRAINING or RESTING outside a quest.");
    }
}
