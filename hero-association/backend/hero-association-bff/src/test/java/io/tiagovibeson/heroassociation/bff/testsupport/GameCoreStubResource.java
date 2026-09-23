package io.tiagovibeson.heroassociation.bff.testsupport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class GameCoreStubResource implements QuarkusTestResourceLifecycleManager {

    private HttpServer server;

    @Override
    public java.util.Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/v1/echo", this::respondToEcho);
            server.start();
            return java.util.Map.of(
                    "hero-association.core.base-url",
                    "http://localhost:" + server.getAddress().getPort());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start the Game Core test server.", exception);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void respondToEcho(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String response = "{\"method\":\"" + exchange.getRequestMethod()
                + "\",\"query\":\"" + escape(exchange.getRequestURI().getRawQuery())
                + "\",\"contentType\":\"" + escape(exchange.getRequestHeaders().getFirst("Content-Type"))
                + "\",\"authorization\":\"" + escape(exchange.getRequestHeaders().getFirst("Authorization"))
                + "\",\"body\":\"" + escape(body) + "\"}";
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
