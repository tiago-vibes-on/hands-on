package io.tiagovibeson.heroassociation.world;

import io.quarkus.runtime.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class WorldDatabaseBootstrap {
    @Inject WorldCatalog catalog;
    @ConfigProperty(name = "hero-association.world.bootstrap-mode") boolean bootstrapMode;
    void start(@Observes StartupEvent event) {
        QuarkusTransaction.requiringNew().run(() -> {
            if (catalog.maps().isEmpty() || catalog.creatures().size() < 2) throw new IllegalStateException("World seed is incomplete.");
            catalog.maps().forEach(map -> catalog.plan(map.definitionId(), map.version()));
        });
        if (bootstrapMode) Quarkus.asyncExit(0);
    }
}
