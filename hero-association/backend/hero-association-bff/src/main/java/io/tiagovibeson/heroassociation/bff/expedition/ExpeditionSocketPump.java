package io.tiagovibeson.heroassociation.bff.expedition;

import static io.tiagovibeson.heroassociation.bff.expedition.ExpeditionSocketUpgradeCheck.LAST_SNAPSHOT;
import static io.tiagovibeson.heroassociation.bff.expedition.ExpeditionSocketUpgradeCheck.OWNER_MANAGER_ID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;

/** Polls only locally subscribed runs; one private Redis-only read per run per tick. */
@ApplicationScoped
public class ExpeditionSocketPump {

    private static final Logger LOG = Logger.getLogger(ExpeditionSocketPump.class);
    private static final int MAX_IN_FLIGHT = 2_048;

    private final OpenConnections connections;
    private final ExpeditionClient expeditions;
    private final ObjectMapper mapper;
    private final Semaphore capacity = new Semaphore(MAX_IN_FLIGHT);
    private final java.util.Set<RunKey> inFlight = ConcurrentHashMap.newKeySet();

    @ConfigProperty(name = "hero-association.expedition.websocket.enabled", defaultValue = "false")
    boolean enabled;

    public ExpeditionSocketPump(OpenConnections connections, ExpeditionClient expeditions,
                                ObjectMapper mapper) {
        this.connections = connections;
        this.expeditions = expeditions;
        this.mapper = mapper;
    }

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        if (!enabled) return;
        Map<RunKey, List<WebSocketConnection>> subscribers = subscribers();
        for (var entry : subscribers.entrySet()) {
            if (!capacity.tryAcquire()) break;
            RunKey run = entry.getKey();
            if (!inFlight.add(run)) {
                capacity.release();
                continue;
            }
            try {
                expeditions.visual(run.ownerManagerId(), run.expeditionId())
                        .whenComplete((response, failure) -> {
                            try {
                                if (failure != null) {
                                    LOG.debugf(failure, "Expedition visual read failed for %s", run.expeditionId());
                                } else if (response.status() == 200) {
                                    publish(run, entry.getValue(), response.body());
                                } else if (response.status() == 404) {
                                    entry.getValue().forEach(connection -> connection.close().subscribe().with(
                                            ignored -> { }, ignored -> { }));
                                }
                            } finally {
                                inFlight.remove(run);
                                capacity.release();
                            }
                        });
            } catch (RuntimeException failure) {
                inFlight.remove(run);
                capacity.release();
                LOG.debugf(failure, "Could not schedule Expedition visual read for %s", run.expeditionId());
            }
        }
    }

    private Map<RunKey, List<WebSocketConnection>> subscribers() {
        Map<RunKey, List<WebSocketConnection>> grouped = new HashMap<>();
        for (WebSocketConnection connection : connections.findByEndpointId(
                ExpeditionSocketUpgradeCheck.ENDPOINT_ID)) {
            if (!connection.isOpen()) continue;
            String owner = connection.userData().get(OWNER_MANAGER_ID);
            if (owner == null) continue;
            RunKey key = new RunKey(UUID.fromString(owner),
                    UUID.fromString(connection.pathParam("expeditionId")));
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(connection);
        }
        return grouped;
    }

    private void publish(RunKey run, List<WebSocketConnection> viewers, String body) {
        try {
            JsonNode snapshot = mapper.readTree(body);
            if (!run.ownerManagerId().toString().equals(snapshot.path("ownerManagerId").asText())
                    || !run.expeditionId().toString().equals(snapshot.path("expeditionId").asText())) {
                LOG.warnf("Expedition visual response identity mismatch for %s", run.expeditionId());
                return;
            }
            String frame = "{\"type\":\"snapshot\",\"snapshot\":" + body + "}";
            for (WebSocketConnection viewer : viewers) {
                if (!viewer.isOpen() || body.equals(viewer.userData().get(LAST_SNAPSHOT))) continue;
                viewer.userData().put(LAST_SNAPSHOT, body);
                viewer.sendText(frame).subscribe().with(ignored -> { }, failure ->
                        LOG.debugf(failure, "Could not send Expedition visual frame"));
            }
        } catch (Exception invalid) {
            LOG.warnf(invalid, "Invalid Expedition visual response for %s", run.expeditionId());
        }
    }

    private record RunKey(UUID ownerManagerId, UUID expeditionId) { }
}
