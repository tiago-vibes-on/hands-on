package io.tiagovibeson.heroassociation.application.exception;

public class ManagerAlreadyExistsException extends RuntimeException {

    public ManagerAlreadyExistsException() {
        super("This account has already completed manager onboarding.");
    }
}
