package io.tiagovibeson.heroassociation.application.exception;

public class ManagerNameAlreadyUsedException extends RuntimeException {

    public ManagerNameAlreadyUsedException(String displayName) {
        super("The manager display name %s is already in use.".formatted(displayName));
    }
}
