package io.tiagovibeson.heroassociation.bff.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class MarketOrderRateLimiterTest {

    @Inject
    MarketOrderRateLimiter limiter;

    @Test
    void limitsOneSubjectAndDoesNotLimitAnother() throws InterruptedException {
        String subject = UUID.randomUUID().toString();
        String otherSubject = UUID.randomUUID().toString();

        for (int attempt = 0; attempt < 5; attempt++) {
            assertTrue(limiter.tryAcquire(subject));
        }
        assertFalse(limiter.tryAcquire(subject));
        assertTrue(limiter.tryAcquire(otherSubject));

        Thread.sleep(1_100);
        assertTrue(limiter.tryAcquire(subject));
    }

    @Test
    void admitsOnlyFiveConcurrentRequestsForTheSameSubject() throws Exception {
        String subject = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int attempt = 0; attempt < 12; attempt++) {
            attempts.add(() -> {
                start.await();
                return limiter.tryAcquire(subject);
            });
        }

        try (var executor = Executors.newFixedThreadPool(attempts.size())) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (Callable<Boolean> attempt : attempts) {
                results.add(executor.submit(attempt));
            }
            start.countDown();
            int admitted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    admitted++;
                }
            }
            assertEquals(5, admitted);
        }
    }
}
