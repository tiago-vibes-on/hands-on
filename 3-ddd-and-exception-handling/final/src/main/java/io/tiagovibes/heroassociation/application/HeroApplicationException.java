package io.tiagovibes.heroassociation.application;

public abstract class HeroApplicationException extends RuntimeException {

    protected HeroApplicationException(String message) {
        super(message);
    }
}
