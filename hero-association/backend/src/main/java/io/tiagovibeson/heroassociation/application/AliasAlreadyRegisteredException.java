package io.tiagovibeson.heroassociation.application;

public class AliasAlreadyRegisteredException extends HeroApplicationException {

    public AliasAlreadyRegisteredException(String alias) {
        super("Alias '" + alias + "' is already registered.");
    }
}
