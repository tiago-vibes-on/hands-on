package io.tiagovibeson.heroassociation.quest;

import io.quarkus.runtime.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class QuestDatabaseBootstrap {
    @Inject QuestTransactions transactions;
    @ConfigProperty(name = "hero-association.quest.bootstrap-mode") boolean bootstrapMode;
    void start(@Observes StartupEvent event) {
        if (transactions.definitions().size() < 3) throw new IllegalStateException("Quest seed is incomplete.");
        if (bootstrapMode) Quarkus.asyncExit(0);
    }
}
