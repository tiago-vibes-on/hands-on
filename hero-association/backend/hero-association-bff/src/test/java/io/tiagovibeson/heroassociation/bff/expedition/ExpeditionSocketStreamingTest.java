package io.tiagovibeson.heroassociation.bff.expedition;

import static io.tiagovibeson.heroassociation.bff.testsupport.ExpeditionSocketFixture.OWNED_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.tiagovibeson.heroassociation.bff.testsupport.GameCoreStubResource;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(GameCoreStubResource.class)
@TestSecurity(user = "test-player")
class ExpeditionSocketStreamingTest {

    @Inject ObjectMapper mapper;

    @Test
    void subscribedSocketReceivesNewVisualFrameWithoutAnotherBrowserRequest() throws Exception {
        LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Origin", "https://heroassociation.test")
                .buildAsync(URI.create("ws://localhost:" + RestAssured.port
                        + "/ws/v1/expeditions/" + OWNED_ID), listener(frames)).join();
        try {
            String opening = frames.poll(5, TimeUnit.SECONDS);
            String update = frames.poll(6, TimeUnit.SECONDS);
            assertNotNull(opening);
            assertNotNull(update);
            assertEquals(0, mapper.readTree(opening).path("snapshot").path("fight")
                    .path("visual").path("elapsedMilliseconds").asLong());
            assertTrue(mapper.readTree(update).path("snapshot").path("fight")
                    .path("visual").path("elapsedMilliseconds").asLong() >= 1_000);
            assertFalse(update.contains("randomSeed"));
            assertFalse(update.contains("X-Hero-Association-Bff-Service-Key"));
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    private WebSocket.Listener listener(LinkedBlockingQueue<String> frames) {
        return new WebSocket.Listener() {
            private final StringBuilder pending = new StringBuilder();

            @Override
            public void onOpen(WebSocket socket) {
                socket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                pending.append(data);
                if (last) {
                    frames.offer(pending.toString());
                    pending.setLength(0);
                }
                socket.request(1);
                return null;
            }
        };
    }
}
