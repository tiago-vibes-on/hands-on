package io.tiagovibeson.heroassociation.assets.application;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class AssetsDatabaseBootstrap {
    @Inject EntityManager em;
    @ConfigProperty(name = "hero-association.assets.bootstrap-mode") boolean bootstrapMode;
    void exitAfterBootstrap(@Observes StartupEvent event) {
        if (!bootstrapMode) return;
        QuarkusTransaction.requiringNew().run(() -> {
            long wallets = em.createQuery("select count(*) from AssetWallet", Long.class).getSingleResult();
            long runes = em.createQuery("select count(*) from Rune", Long.class).getSingleResult();
            long reservations = em.createQuery("select count(*) from AssetReservation", Long.class).getSingleResult();
            if (wallets != 16 || runes != 7 || reservations != 2) throw new IllegalStateException("Assets bootstrap seed is incomplete.");
        });
        Quarkus.asyncExit(0);
    }
}
