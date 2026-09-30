package io.tiagovibeson.heroassociation.application.expedition;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

/** Published only after Core's SQL transaction committed; retry original delivery if this fails. */
@ApplicationScoped
public class ExpeditionAppliedPublisher {

    static final String EXCHANGE = "hero-association.core.expedition-ack.v1";
    static final String ROUTING_KEY = "expedition.applied";
    static final String QUEUE = "hero-association.expedition.settlement-ack.v1";

    private final ConnectionFactory factory = new ConnectionFactory();
    private final ObjectMapper mapper;
    private Connection connection;

    public ExpeditionAppliedPublisher(ObjectMapper mapper,
            @ConfigProperty(name = "hero-association.core.expedition.rabbitmq.host", defaultValue = "localhost") String host,
            @ConfigProperty(name = "hero-association.core.expedition.rabbitmq.port", defaultValue = "15675") int port,
            @ConfigProperty(name = "hero-association.core.expedition.rabbitmq.username", defaultValue = "hero_association_core_settlement") String user,
            @ConfigProperty(name = "hero-association.core.expedition.rabbitmq.password", defaultValue = "hero_association_core_settlement") String password) {
        this.mapper = mapper;
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(user);
        factory.setPassword(password);
        factory.setConnectionTimeout(3_000);
        factory.setHandshakeTimeout(3_000);
        factory.setRequestedHeartbeat(30);
    }

    public void publish(byte[] settlement, UUID expeditionId) {
        try {
            JsonNode root = mapper.readTree(settlement);
            String managerId = root.path("ownerManagerId").asText();
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(settlement));
            byte[] body = mapper.writeValueAsBytes(new Applied(1, expeditionId, UUID.fromString(managerId), digest));
            try (Channel channel = connection().createChannel()) {
                channel.exchangeDeclarePassive(EXCHANGE);
                channel.confirmSelect();
                AtomicBoolean returned = new AtomicBoolean();
                channel.addReturnListener((code, text, exchange, route, properties, payload) -> returned.set(true));
                AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                        .messageId(expeditionId.toString()).contentType("application/json")
                        .contentEncoding("utf-8").deliveryMode(2).build();
                channel.basicPublish(EXCHANGE, ROUTING_KEY, true, properties, body);
                channel.waitForConfirmsOrDie(5_000);
                if (returned.get()) throw new IllegalStateException("Core acknowledgment was not routed.");
            }
        } catch (IOException | TimeoutException | NoSuchAlgorithmException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Core owner-applied acknowledgment was not confirmed.", exception);
        }
    }

    private synchronized Connection connection() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            connection = factory.newConnection("hero-association-core-expedition-applied");
        }
        return connection;
    }

    @PreDestroy
    synchronized void close() throws IOException {
        if (connection != null && connection.isOpen()) connection.close();
    }

    private record Applied(int schemaVersion, UUID expeditionId, UUID ownerManagerId, String digest) { }
}
