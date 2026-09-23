package io.tiagovibeson.heroassociation.application.exception;

public class AgencyMembershipRequiredException extends RuntimeException {

    public AgencyMembershipRequiredException() {
        super("You are not a member of this agency.");
    }
}
