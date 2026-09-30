package io.tiagovibeson.heroassociation.bff.expedition;

import static io.tiagovibeson.heroassociation.bff.expedition.ExpeditionSocketUpgradeCheck.OPENING_SNAPSHOT;
import static io.tiagovibeson.heroassociation.bff.expedition.ExpeditionSocketUpgradeCheck.LAST_SNAPSHOT;

import io.quarkus.security.Authenticated;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

/** Browser socket foundation: one owner-checked snapshot on connection/reconnection. */
@Authenticated
@WebSocket(path = "/ws/v1/expeditions/{expeditionId}", endpointId = "expedition-snapshot")
public class ExpeditionSocket {

    @Inject WebSocketConnection connection;

    @OnOpen
    String openingSnapshot() {
        String snapshot = connection.userData().remove(OPENING_SNAPSHOT);
        if (snapshot == null) {
            connection.closeAndAwait();
            return null;
        }
        connection.userData().put(LAST_SNAPSHOT, snapshot);
        return "{\"type\":\"snapshot\",\"snapshot\":" + snapshot + "}";
    }
}
