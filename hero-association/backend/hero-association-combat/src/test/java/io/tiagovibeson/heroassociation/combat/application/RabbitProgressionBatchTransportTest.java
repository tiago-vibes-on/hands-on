package io.tiagovibeson.heroassociation.combat.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.ConnectionFactory;
import io.tiagovibeson.heroassociation.combat.domain.ProgressionFact;
import io.tiagovibeson.heroassociation.combat.domain.ProgressionFactType;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class RabbitProgressionBatchTransportTest {

    @Test
    void shouldPublishPersistentRoutedEnvelopeToRabbitMq() throws Exception {
        try (var broker = new GenericContainer<>(DockerImageName.parse("rabbitmq:4.2.9-management-alpine"))
                .withEnv("RABBITMQ_DEFAULT_USER", "combat_test")
                .withEnv("RABBITMQ_DEFAULT_PASS", "combat_test")
                .withExposedPorts(5672)) {
            broker.start();
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            var transport = new RabbitProgressionBatchTransport(
                    mapper, broker.getHost(), broker.getMappedPort(5672), "combat_test", "combat_test");
            UUID batchId = UUID.randomUUID();
            UUID battleId = UUID.randomUUID();
            var fact = new ProgressionFact(
                    1, 1_000, ProgressionFactType.STAMINA_ELAPSED,
                    List.of(UUID.randomUUID()), null, null, 1_000, null, 0, 0, null);
            var message = new ProgressionBatchMessage(
                    1, batchId, battleId, 1, 1, Instant.now(), List.of(fact));

            try {
                transport.publish(message);
                ConnectionFactory reader = new ConnectionFactory();
                reader.setHost(broker.getHost());
                reader.setPort(broker.getMappedPort(5672));
                reader.setUsername("combat_test");
                reader.setPassword("combat_test");
                try (var connection = reader.newConnection(); var channel = connection.createChannel()) {
                    var delivery = channel.basicGet(RabbitProgressionBatchTransport.CORE_QUEUE, true);
                    assertNotNull(delivery);
                    assertEquals(batchId.toString(), delivery.getProps().getMessageId());
                    assertEquals(2, delivery.getProps().getDeliveryMode());
                    var json = mapper.readTree(delivery.getBody());
                    assertEquals(1, json.get("schemaVersion").asInt());
                    assertEquals(batchId.toString(), json.get("batchId").asText());
                    assertEquals(battleId.toString(), json.get("battleId").asText());
                    assertEquals(1, json.get("facts").size());
                    assertNull(channel.basicGet(RabbitProgressionBatchTransport.CORE_QUEUE, true));
                }
            } finally {
                transport.close();
            }
        }
    }
}
