package io.tiagovibeson.heroassociation.combat.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class RabbitProgressionBatchTransport implements ProgressionBatchTransport {

    public static final String EXCHANGE = "hero-association.combat.progression.v1";
    public static final String ROUTING_KEY = "core.progression";
    public static final String CORE_QUEUE = "hero-association.core.combat-progression.v1";
    private static final long CONFIRM_TIMEOUT_MILLISECONDS = 5_000;

    private final ObjectMapper objectMapper;
    private final ConnectionFactory factory;
    private Connection connection;

    public RabbitProgressionBatchTransport(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "hero-association.combat.rabbitmq.host") String host,
            @ConfigProperty(name = "hero-association.combat.rabbitmq.port") int port,
            @ConfigProperty(name = "hero-association.combat.rabbitmq.username") String username,
            @ConfigProperty(name = "hero-association.combat.rabbitmq.password") String password) {
        this.objectMapper = objectMapper;
        this.factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setConnectionTimeout(3_000);
        factory.setHandshakeTimeout(3_000);
        factory.setRequestedHeartbeat(30);
    }

    @Override
    public void publish(ProgressionBatchMessage message) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Progression batch could not be serialized.", exception);
        }

        try (Channel channel = connection().createChannel()) {
            channel.exchangeDeclare(EXCHANGE, "direct", true);
            channel.queueDeclare(CORE_QUEUE, true, false, false, null);
            channel.queueBind(CORE_QUEUE, EXCHANGE, ROUTING_KEY);
            channel.confirmSelect();
            AtomicBoolean returned = new AtomicBoolean();
            channel.addReturnListener((replyCode, replyText, exchange, routingKey, properties, returnedBody) ->
                    returned.set(true));
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .messageId(message.batchId().toString())
                    .contentType("application/json")
                    .contentEncoding("utf-8")
                    .deliveryMode(2)
                    .build();
            channel.basicPublish(EXCHANGE, ROUTING_KEY, true, properties, body);
            channel.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MILLISECONDS);
            if (returned.get()) {
                throw new IllegalStateException("Progression batch was not routed to the Core queue.");
            }
        } catch (IOException | TimeoutException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Progression batch was not confirmed by RabbitMQ.", exception);
        }
    }

    private synchronized Connection connection() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            connection = factory.newConnection("hero-association-combat-outbox");
        }
        return connection;
    }

    @PreDestroy
    synchronized void close() throws IOException {
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
    }
}
