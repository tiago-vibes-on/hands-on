package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class RecruitUnavailableException extends RuntimeException {

    public RecruitUnavailableException(UUID recruitId) {
        super("Recruit with id %s is no longer available.".formatted(recruitId));
    }
}
