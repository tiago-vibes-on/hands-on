package io.tiagovibeson.heroassociation.market;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class MarketDatabaseBootstrap {
    @Inject EntityManager em;
    @ConfigProperty(name = "hero-association.market.bootstrap-mode") boolean bootstrapMode;
    void exitAfterBootstrap(@Observes StartupEvent event) {
        if (!bootstrapMode) return;
        // Touch the lazy persistence unit and verify seeds before reporting a successful Job.
        QuarkusTransaction.requiringNew().run(() -> {
            long orders = em.createQuery("select count(*) from MarketOrder", Long.class).getSingleResult();
            long placements = em.createQuery("select count(*) from MarketPlacement", Long.class).getSingleResult();
            if (orders != 2 || placements != 2) throw new IllegalStateException("Market bootstrap requires both deterministic seeded orders and placements.");
        });
        Quarkus.asyncExit(0);
    }
}
