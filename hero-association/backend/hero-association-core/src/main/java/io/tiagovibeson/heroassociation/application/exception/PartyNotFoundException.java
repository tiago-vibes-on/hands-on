package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class PartyNotFoundException extends RuntimeException {

    public PartyNotFoundException(UUID partyId) {
        super("Party with id %s was not found in this agency.".formatted(partyId));
    }
}
