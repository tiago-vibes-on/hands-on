package io.tiagovibeson.heroassociation.expedition;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.rabbitmq.client.ConnectionFactory;
import io.tiagovibeson.heroassociation.domain.UuidV7;

/** Checks Expedition's real settlement message against a disposable k3d broker. */
class K3dRabbitSettlementTransportIT {

    @Test
    void confirmedPublicationIsRoutedToCoreQueueWithExactPayload() throws Exception {
        int port = Integer.parseInt(requiredEnvironment("HERO_ASSOCIATION_COMPONENT_RABBIT_PORT"));
        ConnectionFactory admin = factory(port, "hero_association_expedition",
                requiredEnvironment("HERO_ASSOCIATION_COMPONENT_RABBIT_ADMIN_PASSWORD"));
        UUID expeditionId = UuidV7.next();
        byte[] payload = "{\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8);

        try (var connection = admin.newConnection(); var channel = connection.createChannel()) {
            channel.exchangeDeclarePassive(RabbitSettlementTransport.EXCHANGE);
            channel.queueDeclarePassive(RabbitSettlementTransport.CORE_QUEUE);
            channel.queuePurge(RabbitSettlementTransport.CORE_QUEUE);
            RabbitSettlementTransport publisher = new RabbitSettlementTransport(
                    "127.0.0.1", port, "hero_association_expedition_worker",
                    requiredEnvironment("HERO_ASSOCIATION_COMPONENT_RABBIT_WORKER_PASSWORD"));
            try {
                publisher.publish(payload, expeditionId);
                var delivery = channel.basicGet(RabbitSettlementTransport.CORE_QUEUE, true);
                assertNotNull(delivery);
                assertEquals(expeditionId.toString(), delivery.getProps().getMessageId());
                assertEquals("application/json", delivery.getProps().getContentType());
                assertEquals("utf-8", delivery.getProps().getContentEncoding());
                assertEquals(2, delivery.getProps().getDeliveryMode());
                assertArrayEquals(payload, delivery.getBody());
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
