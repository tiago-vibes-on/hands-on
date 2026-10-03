package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class PartyAwayException extends RuntimeException {

    public PartyAwayException(UUID partyId) {
        super("Party with id %s is on an expedition and its membership cannot change.".formatted(partyId));
    }
}
