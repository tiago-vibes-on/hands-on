package io.tiagovibeson.heroassociation.combat.application;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@Alternative
@Priority(1)
@ApplicationScoped
public class RecordingProgressionBatchTransport implements ProgressionBatchTransport {

    private final List<ProgressionBatchMessage> messages = new ArrayList<>();
    private boolean fail;

    @Override
    public synchronized void publish(ProgressionBatchMessage message) {
        if (fail) {
            throw new IllegalStateException("Simulated broker failure.");
        }
        messages.add(message);
    }

    public synchronized void reset() {
        messages.clear();
        fail = false;
    }

    public synchronized void failNext() {
        fail = true;
    }

    public synchronized List<ProgressionBatchMessage> messages() {
        return List.copyOf(messages);
    }
}
