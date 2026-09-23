package io.tiagovibeson.heroassociation.application.exception;

public class InvalidManagerNameException extends RuntimeException {

    public InvalidManagerNameException() {
        super("A manager display name must contain between 3 and 100 characters.");
    }
}
