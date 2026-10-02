package io.tiagovibeson.heroassociation.assets.api;

import java.util.Optional;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;

@RequestScoped
public class PlayerIdentity {
    @Inject SecurityIdentity identity;
    @ConfigProperty(name = "hero-association.assets.test-access-token") Optional<String> testToken;
    public String subject() {
        return identity.getPrincipal() instanceof JsonWebToken jwt ? jwt.getSubject() : identity.getPrincipal().getName();
    }
    public String token() {
        var credential = identity.getCredential(AccessTokenCredential.class);
        if (credential != null) return credential.getToken();
        if (LaunchMode.current() == LaunchMode.TEST && testToken.isPresent()) return testToken.get() + ":" + subject();
        throw new NotAuthorizedException("Bearer");
    }
}
