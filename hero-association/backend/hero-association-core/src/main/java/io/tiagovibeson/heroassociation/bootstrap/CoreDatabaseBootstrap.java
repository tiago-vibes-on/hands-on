package io.tiagovibeson.heroassociation.bootstrap;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CoreDatabaseBootstrap {

    @ConfigProperty(name = "hero-association.core.bootstrap-mode")
    boolean bootstrapMode;

    @Inject
    EntityManager entityManager;

    void exitAfterBootstrap(@Observes StartupEvent event) {
        if (!bootstrapMode) {
            return;
        }

        Number seededManagers = (Number) entityManager
                .createNativeQuery("select count(*) from manager")
                .getSingleResult();
        if (seededManagers.longValue() == 0) {
            throw new IllegalStateException("Core database bootstrap did not load the seed data.");
        }

        Quarkus.asyncExit(0);
    }
}
