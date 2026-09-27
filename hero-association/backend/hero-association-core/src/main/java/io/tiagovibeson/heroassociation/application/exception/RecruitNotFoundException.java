package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class RecruitNotFoundException extends RuntimeException {

    public RecruitNotFoundException(UUID recruitId) {
        super("Recruit with id %s was not found.".formatted(recruitId));
    }
}
