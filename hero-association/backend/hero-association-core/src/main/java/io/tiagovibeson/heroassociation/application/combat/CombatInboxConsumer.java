package io.tiagovibeson.heroassociation.application.combat;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CombatInboxConsumer {

    private static final Logger LOG = Logger.getLogger(CombatInboxConsumer.class);
    private static final int MAX_MESSAGES_PER_POLL = 100;

    private final CombatInboxService inbox;
    private final ConnectionFactory factory;
    private final String queue;
    private Connection connection;

    @ConfigProperty(name = "hero-association.core.combat-inbox.enabled", defaultValue = "false")
    boolean enabled;

    public CombatInboxConsumer(
            CombatInboxService inbox,
            @ConfigProperty(name = "hero-association.core.combat-inbox.rabbitmq.host") String host,
            @ConfigProperty(name = "hero-association.core.combat-inbox.rabbitmq.port") int port,
            @ConfigProperty(name = "hero-association.core.combat-inbox.rabbitmq.username") String username,
            @ConfigProperty(name = "hero-association.core.combat-inbox.rabbitmq.password") String password,
            @ConfigProperty(name = "hero-association.core.combat-inbox.rabbitmq.queue") String queue) {
        this.inbox = inbox;
        this.queue = queue;
        this.factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setConnectionTimeout(3_000);
        factory.setHandshakeTimeout(3_000);
        factory.setRequestedHeartbeat(30);
    }

    @Scheduled(every = "1s", delayed = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void pollDue() {
        if (enabled) {
            pollAvailable();
        }
    }

    public int pollAvailable() {
        int received = 0;
        try (Channel channel = connection().createChannel()) {
            channel.queueDeclare(queue, true, false, false, null);
            for (int index = 0; index < MAX_MESSAGES_PER_POLL; index++) {
                GetResponse delivery = channel.basicGet(queue, false);
                if (delivery == null) {
                    break;
                }
                long tag = delivery.getEnvelope().getDeliveryTag();
                try {
                    var properties = delivery.getProps();
                    inbox.receive(delivery.getBody(), properties.getMessageId(), properties.getContentType());
                    channel.basicAck(tag, false);
                    received++;
                } catch (RuntimeException exception) {
                    channel.basicNack(tag, false, true);
                    if (exception instanceof IllegalArgumentException) {
                        LOG.warnf("Combat batch %s was rejected and remains queued: %s",
                                delivery.getProps().getMessageId(), exception.getMessage());
                    } else {
                        LOG.errorf(exception,
                                "Combat batch %s was not stored; it remains queued.",
                                delivery.getProps().getMessageId());
                    }
                    break;
                }
            }
        } catch (IOException | TimeoutException exception) {
            LOG.error("Core Combat inbox cannot reach RabbitMQ; queued batches remain for retry.", exception);
        }
        return received;
    }

    private synchronized Connection connection() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            connection = factory.newConnection("hero-association-core-combat-inbox");
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
