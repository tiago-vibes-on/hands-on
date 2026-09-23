package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class RuneNotAvailableException extends RuntimeException {

    public RuneNotAvailableException(UUID runeId) {
        super("Rune with id %s is not available in this agency inventory.".formatted(runeId));
    }
}
