package io.tiagovibeson.heroassociation.application.exception;

public class ManagerOnboardingRequiredException extends RuntimeException {

    public ManagerOnboardingRequiredException() {
        super("Complete manager onboarding before accessing an agency.");
    }
}
