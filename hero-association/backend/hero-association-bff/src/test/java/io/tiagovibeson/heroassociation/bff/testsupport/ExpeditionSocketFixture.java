package io.tiagovibeson.heroassociation.bff.testsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;

/** Shared HTTP fixture so REST proxy and WebSocket tests use the same upstream. */
public final class ExpeditionSocketFixture {

    public static final String OWNER_ID = "019c4c00-0007-7000-8000-000000000201";
    public static final String OWNED_ID = "019c4c00-0007-7000-8000-000000000101";
    public static final String FOREIGN_ID = "019c4c00-0007-7000-8000-000000000102";
    private static final String KEY = "test-only-expedition-bff-service-key-0123456789";
    private static final AtomicInteger VISUAL_READS = new AtomicInteger();

    private ExpeditionSocketFixture() { }

    public static void opening(HttpExchange exchange) throws IOException {
        boolean owned = exchange.getRequestURI().getPath().endsWith("/" + OWNED_ID)
                && "Bearer test-access-token".equals(exchange.getRequestHeaders().getFirst("Authorization"));
        respond(exchange, owned ? 200 : 404,
                owned ? snapshot(0) : "{\"message\":\"Expedition not found.\"}");
    }

    public static void visual(HttpExchange exchange) throws IOException {
        if (!KEY.equals(exchange.getRequestHeaders().getFirst("X-Hero-Association-Bff-Service-Key"))) {
            respond(exchange, 403, "{\"message\":\"Forbidden.\"}");
            return;
        }
        if (!exchange.getRequestURI().getPath().endsWith("/" + OWNER_ID + "/" + OWNED_ID)) {
            respond(exchange, 404, "{\"message\":\"Expedition not found.\"}");
            return;
        }
        respond(exchange, 200, snapshot(VISUAL_READS.incrementAndGet() * 1_000L));
    }

    private static String snapshot(long elapsedMilliseconds) {
        return "{\"ownerManagerId\":\"" + OWNER_ID + "\",\"expeditionId\":\"" + OWNED_ID
                + "\",\"stateVersion\":1,\"phase\":\"FIGHTING\",\"fight\":{\"visual\":{\"elapsedMilliseconds\":"
                + elapsedMilliseconds + "}}}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
