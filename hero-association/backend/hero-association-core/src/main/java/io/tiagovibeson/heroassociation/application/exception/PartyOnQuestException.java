package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class PartyOnQuestException extends RuntimeException {

    public PartyOnQuestException(UUID partyId) {
        super("Party with id %s is on a quest and its membership cannot change.".formatted(partyId));
    }
}
