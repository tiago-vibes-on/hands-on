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
            server.createContext("/api/v1/agencies/", this::respondToEcho);
            server.createContext("/api/v1/recruits/", this::respondToEcho);
            server.createContext("/assets/api/v1/gold-transfers", this::respondToEcho);
            server.createContext("/market/api/v1/market/", this::respondToEcho);
            server.createContext("/expedition/api/v1/expeditions", this::respondToEcho);
            server.createContext("/expedition/api/v1/expeditions/" + ExpeditionSocketFixture.OWNED_ID, ExpeditionSocketFixture::opening);
            server.createContext("/expedition/api/v1/expeditions/" + ExpeditionSocketFixture.FOREIGN_ID, ExpeditionSocketFixture::opening);
            server.createContext("/expedition/internal/v1/expedition-visuals/", ExpeditionSocketFixture::visual);
            server.createContext("/world/api/v1/maps", this::respondToEcho);
            server.createContext("/world/api/v1/creatures", this::respondToEcho);
            server.createContext("/quest/api/v1/quests", this::respondToEcho);
            server.start();
            return java.util.Map.of(
                    "hero-association.world.base-url", "http://localhost:" + server.getAddress().getPort() + "/world",
                    "hero-association.quest.base-url", "http://localhost:" + server.getAddress().getPort() + "/quest",
                    "hero-association.core.base-url",
                    "http://localhost:" + server.getAddress().getPort(),
                    "hero-association.expedition.base-url",
                    "http://localhost:" + server.getAddress().getPort() + "/expedition",
                    "hero-association.assets.base-url",
                    "http://localhost:" + server.getAddress().getPort() + "/assets",
                    "hero-association.market.base-url",
                    "http://localhost:" + server.getAddress().getPort() + "/market");
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
        String response = "{\"path\":\"" + escape(exchange.getRequestURI().getPath())
                + "\",\"method\":\"" + exchange.getRequestMethod()
                + "\",\"query\":\"" + escape(exchange.getRequestURI().getRawQuery())
                + "\",\"contentType\":\"" + escape(exchange.getRequestHeaders().getFirst("Content-Type"))
                + "\",\"authorization\":\"" + escape(exchange.getRequestHeaders().getFirst("Authorization"))
                + "\",\"operationKey\":\"" + escape(exchange.getRequestHeaders().getFirst("X-Operation-Key"))
                + "\",\"traceparent\":\"" + escape(exchange.getRequestHeaders().getFirst("traceparent"))
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
