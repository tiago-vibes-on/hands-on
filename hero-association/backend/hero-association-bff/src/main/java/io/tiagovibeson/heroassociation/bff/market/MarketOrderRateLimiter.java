package io.tiagovibeson.heroassociation.bff.market;

import java.util.UUID;

import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MarketOrderRateLimiter {

    private static final String KEY_PREFIX = "hero-association:market-order-placement:v1:";
    private static final String ACQUIRE_SCRIPT = """
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - 1000)
            if redis.call('ZCARD', KEYS[1]) >= 5 then
                return 0
            end
            redis.call('ZADD', KEYS[1], now, ARGV[1])
            redis.call('PEXPIRE', KEYS[1], 1000)
            return 1
            """;

    @Inject
    RedisDataSource redis;

    public boolean tryAcquire(String subject) {
        return redis.execute("EVAL", ACQUIRE_SCRIPT, "1", KEY_PREFIX + subject, UUID.randomUUID().toString())
                .toInteger() == 1;
    }
}
