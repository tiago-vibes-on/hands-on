package io.tiagovibeson.heroassociation.application.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.domain.CombatProgressionInboxRecord;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@QuarkusTest
class CombatInboxConsumerTest {

    private static final String QUEUE = "hero-association.core.combat-progression.v1";

    @Inject
    CombatInboxService inbox;

    @Inject
    ObjectMapper mapper;

    @Inject
    EntityManager entityManager;

    @Test
    void shouldAckOnlyStoredOrDuplicateBatchesAndRequeueInvalidBatch() throws Exception {
        try (var broker = new GenericContainer<>(DockerImageName.parse("rabbitmq:4.2.9-management-alpine"))
                .withEnv("RABBITMQ_DEFAULT_USER", "core_test")
                .withEnv("RABBITMQ_DEFAULT_PASS", "core_test")
                .withExposedPorts(5672)) {
            broker.start();
            var consumer = new CombatInboxConsumer(
                    inbox, broker.getHost(), broker.getMappedPort(5672),
                    "core_test", "core_test", QUEUE);
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(broker.getHost());
            factory.setPort(broker.getMappedPort(5672));
            factory.setUsername("core_test");
            factory.setPassword("core_test");
            try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
                channel.queueDeclare(QUEUE, true, false, false, null);
                UUID batchId = UuidV7.next();
                byte[] body = CombatInboxTestMessages.batch(
                        mapper, batchId, UuidV7.next(), 1, "STAMINA_ELAPSED");

                publish(channel, batchId, body);
                assertEquals(1, consumer.pollAvailable());
                assertNotNull(entityManager.find(CombatProgressionInboxRecord.class, batchId));
                assertNull(channel.basicGet(QUEUE, true));

                publish(channel, batchId, body);
                assertEquals(1, consumer.pollAvailable());
                assertNull(channel.basicGet(QUEUE, true));
                assertEquals(1, entityManager.createQuery(
                        "select count(i) from CombatProgressionInboxRecord i where i.id = :id",
                        Long.class).setParameter("id", batchId).getSingleResult());

                UUID badBatchId = UuidV7.next();
                ObjectNode invalid = (ObjectNode) mapper.readTree(
                        CombatInboxTestMessages.batch(
                                mapper, badBatchId, UuidV7.next(), 1, "STAMINA_ELAPSED"));
                invalid.put("schemaVersion", 2);
                publish(channel, badBatchId, mapper.writeValueAsBytes(invalid));
                assertEquals(0, consumer.pollAvailable());
                var queued = channel.basicGet(QUEUE, false);
                assertNotNull(queued);
                assertEquals(badBatchId.toString(), queued.getProps().getMessageId());
                channel.basicAck(queued.getEnvelope().getDeliveryTag(), false);
            } finally {
                consumer.close();
            }
        }
    }

    private void publish(com.rabbitmq.client.Channel channel, UUID batchId, byte[] body) throws Exception {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .messageId(batchId.toString())
                .contentType("application/json")
                .deliveryMode(2)
                .build();
        channel.basicPublish("", QUEUE, properties, body);
    }
}
