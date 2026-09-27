package io.tiagovibeson.heroassociation.application.exception;

public class AgencyNameAlreadyUsedException extends RuntimeException {

    public AgencyNameAlreadyUsedException(String name) {
        super("The agency name %s is already in use.".formatted(name));
    }
}
