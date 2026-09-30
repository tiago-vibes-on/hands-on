package io.tiagovibeson.heroassociation.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.tiagovibeson.heroassociation.domain.UuidV7;

class CoreAdmissionClientTest {

    @Test
    void forwardsPlayerTokenAndMapsOnlyTheTrustedCoreBaseline() throws Exception {
        UUID expeditionId = UuidV7.next();
        UUID managerId = UuidV7.next();
        UUID agencyId = UuidV7.next();
        UUID partyId = UuidV7.next();
        UUID mapId = UuidV7.next();
        UUID heroId = UuidV7.next();
        UUID creatureId = UuidV7.next();
        String response = ("""
                {"expeditionId":"%s","ownerManagerId":"%s","agencyId":"%s","partyId":"%s",
                 "mapId":"%s","mapVersion":1,
                 "baseline":{"heroes":[{"heroId":"%s","name":"Test Warrior","heroClass":"WARRIOR",
                   "experience":0,"skillPoints":{"MELEE":0.000000,"DISTANCE":0.000000,
                   "MAGIC":0.000000,"SHIELD":0.000000},"health":300,"mana":50,
                   "staminaMilliseconds":172800000,"previousActivity":"RESTING","runeIds":{},
                   "criticalChance":0.01,"criticalDamageMultiplier":2.1}]},
                 "creature":{"definitionId":"%s","name":"Troll","version":1,
                   "baseExperience":100,"maxHealth":2000,"maxMana":100,"attackDamage":10,
                   "attackIntervalMilliseconds":1600,"healthRecoveryPerSecond":0,
                   "manaRecoveryPerSecond":0,"criticalChance":0.1,"criticalDamageMultiplier":2.0}}
                """).formatted(expeditionId, managerId, agencyId, partyId, mapId, heroId, creatureId);
        AtomicBoolean authenticated = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/expedition-admissions/reservations", exchange -> {
            authenticated.set("Bearer player-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    && "test-service-key-with-at-least-32-characters".equals(
                    exchange.getRequestHeaders().getFirst("X-Hero-Association-Service-Key")));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            CoreAdmissionClient client = new CoreAdmissionClient(new ObjectMapper());
            client.coreBaseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            client.serviceKey = java.util.Optional.of("test-service-key-with-at-least-32-characters");
            PreparedEntry entry = client.reserve(expeditionId, agencyId, partyId, mapId, "player-token");
            assertTrue(authenticated.get());
            assertEquals(managerId, entry.ownerManagerId());
            assertEquals(heroId, entry.heroes().getFirst().heroId());
            assertEquals(0.01, entry.heroes().getFirst().criticalChance());
            assertEquals(2000, entry.creature().maxHealth());
            assertEquals(100, entry.creature().baseExperience());
        } finally {
            server.stop(0);
        }
    }
}
