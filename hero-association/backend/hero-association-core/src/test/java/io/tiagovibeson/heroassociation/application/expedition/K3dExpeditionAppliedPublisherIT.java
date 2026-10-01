package io.tiagovibeson.heroassociation.application.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.ConnectionFactory;
import io.tiagovibeson.heroassociation.domain.UuidV7;

/** Checks Core's real settlement-ack message against a disposable k3d broker. */
class K3dExpeditionAppliedPublisherIT {

    @Test
    void ownerAcknowledgmentIsConfirmedRoutedAndReferencesExactPayload() throws Exception {
        int port = Integer.parseInt(requiredEnvironment("HERO_ASSOCIATION_COMPONENT_RABBIT_PORT"));
        ConnectionFactory admin = factory(port, "hero_association_expedition",
                requiredEnvironment("HERO_ASSOCIATION_COMPONENT_RABBIT_ADMIN_PASSWORD"));
        UUID expeditionId = UuidV7.next();
        UUID managerId = UuidV7.next();
        byte[] settlement = ("{\"ownerManagerId\":\"" + managerId + "\"}")
                .getBytes(StandardCharsets.UTF_8);

        try (var connection = admin.newConnection(); var channel = connection.createChannel()) {
            channel.exchangeDeclarePassive(ExpeditionAppliedPublisher.EXCHANGE);
            channel.queueDeclarePassive(ExpeditionAppliedPublisher.QUEUE);
            channel.queuePurge(ExpeditionAppliedPublisher.QUEUE);
            ExpeditionAppliedPublisher publisher = new ExpeditionAppliedPublisher(new ObjectMapper(),
                    "127.0.0.1", port, "hero_association_core_settlement",
                    requiredEnvironment("HERO_ASSOCIATION_COMPONENT_RABBIT_CORE_PASSWORD"));
            try {
                publisher.publish(settlement, expeditionId);
                var delivery = channel.basicGet(ExpeditionAppliedPublisher.QUEUE, true);
                assertNotNull(delivery);
                assertEquals(expeditionId.toString(), delivery.getProps().getMessageId());
                assertEquals("application/json", delivery.getProps().getContentType());
                assertEquals(2, delivery.getProps().getDeliveryMode());
                var body = new ObjectMapper().readTree(delivery.getBody());
                assertEquals(1, body.path("schemaVersion").asInt());
                assertEquals(expeditionId.toString(), body.path("expeditionId").asText());
                assertEquals(managerId.toString(), body.path("ownerManagerId").asText());
                assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(settlement)), body.path("digest").asText());
            } finally {
                publisher.close();
            }
        }
    }

    private static ConnectionFactory factory(int port, String user, String password) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setPort(port);
        factory.setUsername(user);
        factory.setPassword(password);
        factory.setConnectionTimeout(3_000);
        factory.setHandshakeTimeout(3_000);
        return factory;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing disposable k3d RabbitMQ test environment: " + name);
        }
        return value;
    }
}
