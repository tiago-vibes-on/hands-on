package io.tiagovibeson.heroassociation.application.exception;

public class PartyNameAlreadyUsedException extends RuntimeException {

    public PartyNameAlreadyUsedException(String name) {
        super("A party named %s already exists in this agency.".formatted(name));
    }
}
