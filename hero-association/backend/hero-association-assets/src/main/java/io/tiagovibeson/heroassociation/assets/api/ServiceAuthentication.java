package io.tiagovibeson.heroassociation.assets.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.ServiceUnavailableException;

@ApplicationScoped
public class ServiceAuthentication {
    @ConfigProperty(name = "hero-association.assets.market-service-key") Optional<String> marketKey;
    @ConfigProperty(name = "hero-association.assets.core-service-key") Optional<String> coreKey;
    public void market(String key) { require(marketKey, key); }
    public void core(String key) { require(coreKey, key); }
    private void require(Optional<String> configured, String provided) {
        String expected = configured.orElse("");
        if (expected.length() < 32) throw new ServiceUnavailableException("Private Assets credentials are not configured.");
        if (provided == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8)))
            throw new ForbiddenException();
    }
}
