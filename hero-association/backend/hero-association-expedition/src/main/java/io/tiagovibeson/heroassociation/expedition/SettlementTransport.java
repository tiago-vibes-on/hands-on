package io.tiagovibeson.heroassociation.expedition;

import java.util.UUID;

/** Returns only after a mandatory, routed, durable broker confirmation. */
public interface SettlementTransport {
    void publish(byte[] body, UUID expeditionId);
}
