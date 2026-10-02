package io.tiagovibeson.heroassociation.assets.testsupport;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.net.InetSocketAddress;
import java.util.Map;

public class CoreAuthorityStubResource implements QuarkusTestResourceLifecycleManager {
    public static final String CORE_KEY = "test-only-assets-core-service-key-0123456789";
    public static final String MARKET_KEY = "test-only-assets-market-service-key-0123456789";
    public static final String MANAGER = "019c4c00-0000-7000-8000-000000000204";
    public static final String AGENCY = "019c4c00-0001-7000-8000-000000000001";
    public static volatile boolean unavailable;
    public static volatile int calls;
    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    @Override public Map<String,String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/internal/v1/asset-authority", exchange -> {
                calls++; int status = unavailable ? 503 : 200;
                if (!CORE_KEY.equals(exchange.getRequestHeaders().getFirst("X-Hero-Association-Assets-Core-Service-Key"))) status=403;
                JsonNode body = mapper.readTree(exchange.getRequestBody().readAllBytes());
                var result = mapper.createObjectNode();
                if (exchange.getRequestURI().getPath().endsWith("/transfers")) {
                    if ("AGENCY_TO_MANAGER".equals(body.path("direction").asText()) && !java.util.Set.of("Bearer leader-token", "Bearer assets-test-player-token:leader").contains(exchange.getRequestHeaders().getFirst("Authorization"))) status=403;
                    if (!"dawnwatch agency".equals(body.path("agencyName").asText())) status=404;
                    result.put("requesterManagerId",MANAGER).put("managerId",MANAGER).put("managerName","Manager 4").put("agencyId",AGENCY).put("agencyName","Dawnwatch Agency");
                } else {
                    String type = body.path("ownerType").asText(), id=body.path("ownerId").isNull() ? MANAGER : body.path("ownerId").asText();
                    if ("MANAGER".equals(type) && !MANAGER.equals(id)) status=403;
                    if ("AGENCY".equals(type) && !java.util.Set.of("Bearer leader-token", "Bearer assets-test-player-token:leader").contains(exchange.getRequestHeaders().getFirst("Authorization"))) status=403;
                    result.put("managerId",MANAGER).put("ownerType",type).put("ownerId",id).put("ownerName","Fixture Owner");
                }
                if (status != 200) result = mapper.createObjectNode().put("message","Core authorization unavailable or denied.");
                byte[] bytes=mapper.writeValueAsBytes(result);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });server.start();return Map.of("hero-association.core.base-url","http://127.0.0.1:"+server.getAddress().getPort(),"hero-association.assets.core-service-key",CORE_KEY,"hero-association.assets.market-service-key",MARKET_KEY);
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    @Override public void stop() { if(server!=null)server.stop(0); }
}
