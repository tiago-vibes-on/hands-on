package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class AgencyNotFoundException extends RuntimeException {

    public AgencyNotFoundException(UUID agencyId) {
        super("Agency with id %s was not found.".formatted(agencyId));
    }
}
