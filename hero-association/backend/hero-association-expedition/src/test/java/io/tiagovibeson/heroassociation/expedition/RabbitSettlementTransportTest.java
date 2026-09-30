package io.tiagovibeson.heroassociation.expedition;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.rabbitmq.client.ConnectionFactory;
import io.tiagovibeson.heroassociation.domain.UuidV7;

class RabbitSettlementTransportTest {

    @Test
    void confirmedPublicationIsRoutedToDurableCoreQueue() throws Exception {
        try (GenericContainer<?> rabbit = new GenericContainer<>(
                DockerImageName.parse("rabbitmq:4.2.9-management-alpine"))
                .withExposedPorts(5672)
                .withEnv("RABBITMQ_DEFAULT_USER", "settlement_test")
                .withEnv("RABBITMQ_DEFAULT_PASS", "settlement_test")) {
            rabbit.start();
            ConnectionFactory setup = new ConnectionFactory();
            setup.setHost(rabbit.getHost());
            setup.setPort(rabbit.getMappedPort(5672));
            setup.setUsername("settlement_test");
            setup.setPassword("settlement_test");
            try (var connection = setup.newConnection(); var channel = connection.createChannel()) {
                channel.exchangeDeclare(RabbitSettlementTransport.EXCHANGE, "direct", true);
                channel.queueDeclare(RabbitSettlementTransport.CORE_QUEUE, true, false, false, null);
                channel.queueBind(RabbitSettlementTransport.CORE_QUEUE,
                        RabbitSettlementTransport.EXCHANGE, RabbitSettlementTransport.ROUTING_KEY);
            }
            assertEquals(0, rabbit.execInContainer("rabbitmqctl", "add_user",
                    "expedition_worker_test", "expedition_worker_test").getExitCode());
            assertEquals(0, rabbit.execInContainer("rabbitmqctl", "set_permissions", "-p", "/",
                    "expedition_worker_test", "^$",
                    "^hero-association[.]expedition[.]settlement[.]v1$",
                    "^hero-association[.]expedition[.]settlement-ack[.]v1$").getExitCode());
            int port = rabbit.getMappedPort(5672);
            UUID expeditionId = UuidV7.next();
            byte[] payload = "{\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8);
            RabbitSettlementTransport publisher = new RabbitSettlementTransport(
                    rabbit.getHost(), port, "expedition_worker_test", "expedition_worker_test");
            try {
                publisher.publish(payload, expeditionId);
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(rabbit.getHost());
                factory.setPort(port);
                factory.setUsername("settlement_test");
                factory.setPassword("settlement_test");
                try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
                    var delivery = channel.basicGet(RabbitSettlementTransport.CORE_QUEUE, true);
                    assertNotNull(delivery);
                    assertEquals(expeditionId.toString(), delivery.getProps().getMessageId());
                    assertEquals("application/json", delivery.getProps().getContentType());
                    assertEquals(2, delivery.getProps().getDeliveryMode());
                    assertArrayEquals(payload, delivery.getBody());
                }
                ConnectionFactory restricted = new ConnectionFactory();
                restricted.setHost(rabbit.getHost());
                restricted.setPort(port);
                restricted.setUsername("expedition_worker_test");
                restricted.setPassword("expedition_worker_test");
                try (var connection = restricted.newConnection()) {
                    var channel = connection.createChannel();
                    assertThrows(IOException.class, () -> channel.basicGet(RabbitSettlementTransport.CORE_QUEUE, true));
                }
            } finally {
                publisher.close();
            }
        }
    }
}
