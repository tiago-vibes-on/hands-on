package io.tiagovibeson.heroassociation.bff.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.tiagovibeson.heroassociation.bff.testsupport.GameCoreStubResource;

@QuarkusTest
@QuarkusTestResource(GameCoreStubResource.class)
@TestSecurity(user = "test-player")
class ExpeditionSocketTest {

    private static final String OWNED_ID = "019c4c00-0007-7000-8000-000000000101";
    private static final String FOREIGN_ID = "019c4c00-0007-7000-8000-000000000102";

    @Test
    void ownerReceivesOneOpeningSnapshotWithoutHiddenEngineState() throws Exception {
        CompletableFuture<String> first = new CompletableFuture<>();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Origin", "https://heroassociation.test")
                .buildAsync(uri(OWNED_ID), listener(first)).join();
        String frame = first.get(5, TimeUnit.SECONDS);
        assertTrue(frame.contains("\"type\":\"snapshot\""));
        assertTrue(frame.contains("\"expeditionId\":\"" + OWNED_ID + "\""));
        assertFalse(frame.contains("randomSeed"));
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @Test
    void rejectsCrossSiteAndMissingOriginBeforeUpgrade() {
        assertHandshakeStatus(OWNED_ID, "https://evil.test", 403);
        assertHandshakeStatus(OWNED_ID, null, 403);
    }

    @Test
    void rejectsForeignAndMalformedRunIdsBeforeUpgrade() {
        assertHandshakeStatus(FOREIGN_ID, "https://heroassociation.test", 404);
        assertHandshakeStatus("not-a-uuid", "https://heroassociation.test", 400);
    }

    @Test
    @TestSecurity(user = "", authorizationEnabled = true)
    void rejectsAnonymousUpgrade() {
        assertHandshakeStatus(OWNED_ID, "https://heroassociation.test", 401);
    }

    private void assertHandshakeStatus(String id, String origin, int status) {
        var builder = HttpClient.newHttpClient().newWebSocketBuilder();
        if (origin != null) builder.header("Origin", origin);
        try {
            builder.buildAsync(uri(id), listener(new CompletableFuture<>())).join();
            throw new AssertionError("Expected WebSocket handshake rejection");
        } catch (CompletionException exception) {
            assertTrue(exception.getCause() instanceof WebSocketHandshakeException);
            assertEquals(status, ((WebSocketHandshakeException) exception.getCause()).getResponse().statusCode());
        }
    }

    private URI uri(String id) {
        return URI.create("ws://localhost:" + RestAssured.port + "/ws/v1/expeditions/" + id);
    }

    private WebSocket.Listener listener(CompletableFuture<String> first) {
        return new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                if (last) first.complete(data.toString());
                webSocket.request(1);
                return null;
            }
        };
    }
}
