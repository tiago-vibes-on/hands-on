package io.tiagovibeson.heroassociation.application.expedition;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

/** Ack the original delivery only after both SQL application and the separate owner ack publish. */
@ApplicationScoped
public class ExpeditionSettlementConsumer {

    private static final Logger LOG = Logger.getLogger(ExpeditionSettlementConsumer.class);
    private static final String EXCHANGE = "hero-association.expedition.settlement.v1";
    private static final String ROUTING_KEY = "core.apply";
    private static final String QUEUE = "hero-association.core.expedition-settlement.v1";

    private final ExpeditionSettlementService service;
    private final ExpeditionAppliedPublisher appliedPublisher;
    private final ConnectionFactory factory = new ConnectionFactory();
    private Connection connection;

    @ConfigProperty(name = "hero-association.core.expedition.enabled", defaultValue = "false")
    boolean enabled;

    public ExpeditionSettlementConsumer(ExpeditionSettlementService service,
            ExpeditionAppliedPublisher appliedPublisher,
            @ConfigProperty(name = "hero-association.core.expedition.rabbitmq.host", defaultValue = "localhost") String host,
            @ConfigProperty(name = "hero-association.core.expedition.rabbitmq.port", defaultValue = "15675") int port,
            @ConfigProperty(name = "hero-association.core.expedition.rabbitmq.username", defaultValue = "hero_association_core_settlement") String user,
            @ConfigProperty(name = "hero-association.core.expedition.rabbitmq.password", defaultValue = "hero_association_core_settlement") String password) {
        this.service = service;
        this.appliedPublisher = appliedPublisher;
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(user);
        factory.setPassword(password);
        factory.setConnectionTimeout(3_000);
        factory.setHandshakeTimeout(3_000);
        factory.setRequestedHeartbeat(30);
    }

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledPoll() {
        if (enabled) pollAvailable();
    }

    public int pollAvailable() {
        int acknowledged = 0;
        try (Channel channel = connection().createChannel()) {
            channel.queueDeclarePassive(QUEUE);
            for (int index = 0; index < 64; index++) {
                GetResponse delivery = channel.basicGet(QUEUE, false);
                if (delivery == null) break;
                try {
                    var properties = delivery.getProps();
                    service.apply(delivery.getBody(), properties.getMessageId(), properties.getContentType());
                    appliedPublisher.publish(delivery.getBody(), UUID.fromString(properties.getMessageId()));
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    acknowledged++;
                } catch (RuntimeException exception) {
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                    LOG.errorf(exception, "Expedition settlement %s remains queued",
                            delivery.getProps().getMessageId());
                    break;
                }
            }
        } catch (IOException | TimeoutException exception) {
            LOG.error("Core cannot consume Expedition settlements.", exception);
        }
        return acknowledged;
    }

    private synchronized Connection connection() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            connection = factory.newConnection("hero-association-core-expedition-settlement");
        }
        return connection;
    }

    @PreDestroy
    synchronized void close() throws IOException {
        if (connection != null && connection.isOpen()) connection.close();
    }
}
