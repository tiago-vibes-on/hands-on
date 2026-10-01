package io.tiagovibeson.heroassociation.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownSignalException;

/** Exercises the disposable k3d broker's real AMQP permissions, not a local container. */
class K3dRabbitPermissionsIT {

    @Test
    void expeditionWorkerCannotReadCoreSettlementQueue() throws Exception {
        assertReadDenied("hero_association_expedition_worker",
                requiredEnvironment("HERO_ASSOCIATION_E2E_RABBIT_WORKER_PASSWORD"),
                RabbitSettlementTransport.CORE_QUEUE);
    }

    @Test
    void coreSettlementUserCannotReadExpeditionAckQueue() throws Exception {
        assertReadDenied("hero_association_core_settlement",
                requiredEnvironment("HERO_ASSOCIATION_E2E_RABBIT_CORE_PASSWORD"),
                RabbitSettlementTransport.ACK_QUEUE);
    }

    private static void assertReadDenied(String user, String password, String queue) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setPort(Integer.parseInt(requiredEnvironment("HERO_ASSOCIATION_E2E_RABBIT_PORT")));
        factory.setUsername(user);
        factory.setPassword(password);
        factory.setConnectionTimeout(3_000);
        factory.setHandshakeTimeout(3_000);

        try (var connection = factory.newConnection()) {
            var channel = connection.createChannel();
            IOException failure = assertThrows(IOException.class,
                    () -> channel.basicGet(queue, false));
            if (!(failure.getCause() instanceof ShutdownSignalException shutdown)
                    || !(shutdown.getReason() instanceof AMQP.Channel.Close close)) {
                throw new AssertionError("Expected RabbitMQ to close the channel for denied queue access", failure);
            }
            assertEquals(403, close.getReplyCode());
            assertTrue(close.getReplyText().contains("read access to queue '" + queue + "'"));
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required disposable k3d RabbitMQ test environment: " + name);
        }
        return value;
    }
}
