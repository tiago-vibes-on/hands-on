package io.tiagovibeson.heroassociation.application.exception;

public class ManagerAlreadyInAgencyException extends RuntimeException {

    public ManagerAlreadyInAgencyException() {
        super("Leave your current agency before creating another one.");
    }
}
