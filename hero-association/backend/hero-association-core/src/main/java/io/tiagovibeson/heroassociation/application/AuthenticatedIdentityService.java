package io.tiagovibeson.heroassociation.application;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthenticatedIdentityService {

    @Inject
    SecurityIdentity securityIdentity;

    public AuthenticatedIdentity currentIdentity() {
        if (securityIdentity.getPrincipal() instanceof JsonWebToken token) {
            return new AuthenticatedIdentity(
                    token.getSubject(),
                    token.getClaim("email"),
                    Boolean.TRUE.equals(token.getClaim("email_verified")));
        }
        return new AuthenticatedIdentity(securityIdentity.getPrincipal().getName(), null, false);
    }
}
