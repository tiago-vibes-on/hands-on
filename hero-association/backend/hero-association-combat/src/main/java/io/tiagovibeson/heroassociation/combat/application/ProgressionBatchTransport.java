package io.tiagovibeson.heroassociation.combat.application;

public interface ProgressionBatchTransport {
    void publish(ProgressionBatchMessage message);
}
