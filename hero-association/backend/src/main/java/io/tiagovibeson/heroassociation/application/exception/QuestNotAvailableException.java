package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class QuestNotAvailableException extends RuntimeException {

    public QuestNotAvailableException(UUID questId) {
        super("Quest with id %s is not available to start.".formatted(questId));
    }
}
