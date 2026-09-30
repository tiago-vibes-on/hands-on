package io.tiagovibeson.heroassociation.application.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.ConnectionFactory;
import io.tiagovibeson.heroassociation.domain.UuidV7;

class ExpeditionAppliedPublisherTest {

    @Test
    void ownerAcknowledgmentIsDurableRoutedAndReferencesExactPayload() throws Exception {
        try (GenericContainer<?> rabbit = new GenericContainer<>(
                DockerImageName.parse("rabbitmq:4.2.9-management-alpine"))
                .withExposedPorts(5672)
                .withEnv("RABBITMQ_DEFAULT_USER", "applied_test")
                .withEnv("RABBITMQ_DEFAULT_PASS", "applied_test")) {
            rabbit.start();
            ConnectionFactory setup = new ConnectionFactory();
            setup.setHost(rabbit.getHost());
            setup.setPort(rabbit.getMappedPort(5672));
            setup.setUsername("applied_test");
            setup.setPassword("applied_test");
            try (var connection = setup.newConnection(); var channel = connection.createChannel()) {
                channel.exchangeDeclare(ExpeditionAppliedPublisher.EXCHANGE, "direct", true);
                channel.queueDeclare(ExpeditionAppliedPublisher.QUEUE, true, false, false, null);
                channel.queueBind(ExpeditionAppliedPublisher.QUEUE,
                        ExpeditionAppliedPublisher.EXCHANGE, ExpeditionAppliedPublisher.ROUTING_KEY);
            }
            UUID expeditionId = UuidV7.next();
            UUID managerId = UuidV7.next();
            byte[] settlement = ("{\"ownerManagerId\":\"" + managerId + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            ExpeditionAppliedPublisher publisher = new ExpeditionAppliedPublisher(new ObjectMapper(),
                    rabbit.getHost(), rabbit.getMappedPort(5672), "applied_test", "applied_test");
            try {
                publisher.publish(settlement, expeditionId);
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(rabbit.getHost());
                factory.setPort(rabbit.getMappedPort(5672));
                factory.setUsername("applied_test");
                factory.setPassword("applied_test");
                try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
                    var delivery = channel.basicGet(ExpeditionAppliedPublisher.QUEUE, true);
                    assertNotNull(delivery);
                    assertEquals(expeditionId.toString(), delivery.getProps().getMessageId());
                    assertEquals(2, delivery.getProps().getDeliveryMode());
                    var body = new ObjectMapper().readTree(delivery.getBody());
                    assertEquals(expeditionId.toString(), body.path("expeditionId").asText());
                    assertEquals(managerId.toString(), body.path("ownerManagerId").asText());
                    assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(settlement)), body.path("digest").asText());
                }
            } finally {
                publisher.close();
            }
        }
    }
}
