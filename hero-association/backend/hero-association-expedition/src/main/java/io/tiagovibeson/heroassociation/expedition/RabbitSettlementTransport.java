package io.tiagovibeson.heroassociation.expedition;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

/** Broker confirmation means delivery to the durable Core queue, not owner application. */
@ApplicationScoped
public class RabbitSettlementTransport implements SettlementTransport {

    public static final String EXCHANGE = "hero-association.expedition.settlement.v1";
    public static final String ROUTING_KEY = "core.apply";
    public static final String CORE_QUEUE = "hero-association.core.expedition-settlement.v1";
    public static final String ACK_EXCHANGE = "hero-association.core.expedition-ack.v1";
    public static final String ACK_ROUTING_KEY = "expedition.applied";
    public static final String ACK_QUEUE = "hero-association.expedition.settlement-ack.v1";

    private final ConnectionFactory factory = new ConnectionFactory();
    private Connection connection;

    public RabbitSettlementTransport(
            @ConfigProperty(name = "expedition.rabbitmq.host", defaultValue = "localhost") String host,
            @ConfigProperty(name = "expedition.rabbitmq.port", defaultValue = "15675") int port,
            @ConfigProperty(name = "expedition.rabbitmq.username", defaultValue = "hero_association_expedition_worker") String user,
            @ConfigProperty(name = "expedition.rabbitmq.password", defaultValue = "hero_association_expedition_worker") String password) {
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(user);
        factory.setPassword(password);
        factory.setConnectionTimeout(3_000);
        factory.setHandshakeTimeout(3_000);
        factory.setRequestedHeartbeat(30);
    }

    public void publish(byte[] body, UUID expeditionId) {
        try (Channel channel = connection().createChannel()) {
            channel.exchangeDeclarePassive(EXCHANGE);
            channel.confirmSelect();
            AtomicBoolean returned = new AtomicBoolean();
            channel.addReturnListener((code, text, exchange, route, properties, payload) -> returned.set(true));
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .messageId(expeditionId.toString())
                    .contentType("application/json")
                    .contentEncoding("utf-8")
                    .deliveryMode(2)
                    .build();
            channel.basicPublish(EXCHANGE, ROUTING_KEY, true, properties, body);
            channel.waitForConfirmsOrDie(5_000);
            if (returned.get()) {
                throw new IllegalStateException("Settlement was not routed to Core.");
            }
        } catch (IOException | TimeoutException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Settlement was not confirmed by RabbitMQ.", exception);
        }
    }

    private synchronized Connection connection() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            connection = factory.newConnection("hero-association-expedition-settlement");
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
