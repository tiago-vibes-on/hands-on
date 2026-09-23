package io.tiagovibeson.heroassociation.application.exception;

public class AgencyLeaderRequiredException extends RuntimeException {

    public AgencyLeaderRequiredException() {
        super("Only an agency leader can perform this action.");
    }
}
