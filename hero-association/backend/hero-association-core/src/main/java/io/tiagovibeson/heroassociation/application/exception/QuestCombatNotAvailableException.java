package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class QuestCombatNotAvailableException extends RuntimeException {

    public QuestCombatNotAvailableException(UUID questId) {
        super("Quest with id %s does not have an active combat encounter.".formatted(questId));
    }
}
