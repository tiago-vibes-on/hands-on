package io.tiagovibeson.heroassociation.expedition;

import java.io.IOException;
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

@ApplicationScoped
public class SettlementAckConsumer {

    private static final Logger LOG = Logger.getLogger(SettlementAckConsumer.class);
    private final SettlementAckHandler handler;
    private final ConnectionFactory factory = new ConnectionFactory();
    private Connection connection;

    @ConfigProperty(name = "expedition.settlement.enabled", defaultValue = "false")
    boolean enabled;

    public SettlementAckConsumer(
            SettlementAckHandler handler,
            @ConfigProperty(name = "expedition.rabbitmq.host", defaultValue = "localhost") String host,
            @ConfigProperty(name = "expedition.rabbitmq.port", defaultValue = "15675") int port,
            @ConfigProperty(name = "expedition.rabbitmq.username", defaultValue = "hero_association_expedition_worker") String user,
            @ConfigProperty(name = "expedition.rabbitmq.password", defaultValue = "hero_association_expedition_worker") String password) {
        this.handler = handler;
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
        if (enabled) {
            pollAvailable();
        }
    }

    public int pollAvailable() {
        int applied = 0;
        try (Channel channel = connection().createChannel()) {
            channel.queueDeclarePassive(RabbitSettlementTransport.ACK_QUEUE);
            for (int index = 0; index < 64; index++) {
                GetResponse delivery = channel.basicGet(RabbitSettlementTransport.ACK_QUEUE, false);
                if (delivery == null) {
                    break;
                }
                try {
                    var properties = delivery.getProps();
                    handler.accept(delivery.getBody(), properties.getMessageId(), properties.getContentType());
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    applied++;
                } catch (RuntimeException exception) {
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                    LOG.errorf(exception, "Owner acknowledgment %s remains queued",
                            delivery.getProps().getMessageId());
                    break;
                }
            }
        } catch (IOException | TimeoutException exception) {
            LOG.error("Expedition cannot consume Core owner acknowledgments.", exception);
        }
        return applied;
    }

    private synchronized Connection connection() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            connection = factory.newConnection("hero-association-expedition-settlement-ack");
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
