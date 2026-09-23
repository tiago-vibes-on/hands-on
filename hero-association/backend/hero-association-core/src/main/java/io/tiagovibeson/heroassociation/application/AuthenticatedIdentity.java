package io.tiagovibeson.heroassociation.application;

public record AuthenticatedIdentity(String keycloakSubject, String email, boolean emailVerified) {
}
