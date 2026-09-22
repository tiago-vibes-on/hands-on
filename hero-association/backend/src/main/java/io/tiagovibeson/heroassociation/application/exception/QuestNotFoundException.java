package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class QuestNotFoundException extends RuntimeException {

    public QuestNotFoundException(UUID questId) {
        super("Quest with id %s was not found in this agency.".formatted(questId));
    }
}
