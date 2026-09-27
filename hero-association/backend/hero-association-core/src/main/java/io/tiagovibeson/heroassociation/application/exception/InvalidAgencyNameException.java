package io.tiagovibeson.heroassociation.application.exception;

public class InvalidAgencyNameException extends RuntimeException {

    public InvalidAgencyNameException() {
        super("An agency name must contain between 3 and 100 characters.");
    }
}
