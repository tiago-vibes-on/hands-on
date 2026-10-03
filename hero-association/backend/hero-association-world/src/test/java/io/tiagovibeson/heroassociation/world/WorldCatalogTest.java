package io.tiagovibeson.heroassociation.world;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.tiagovibeson.heroassociation.contract.WorldContract.*;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;

@QuarkusTest
class WorldCatalogTest {
    private static final UUID FIELD = UUID.fromString("019c4c00-0006-7000-8000-000000000001");
    private static final String KEY = "test-world-service-key-01234567890123456789";
    @Inject WorldCatalog catalog;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;

    @Test void theSeedPinsTheExistingThreeTrollEncounter() {
        Plan plan = catalog.plan(FIELD, null);
        assertEquals(2000, plan.creatures().getFirst().maxHealth());
        assertEquals(3, plan.map().encounter(1).spawns().getFirst().count());
        assertEquals(plan.map().encounter(1), plan.map().encounter(100));
        assertFalse(plan.map().completedAfter(100));
    }

    @Test void publishingNewVersionsCannotChangeAnExistingPlan() throws Exception {
        Plan pinned = catalog.plan(FIELD, 1);
        Creature old = pinned.creatures().getFirst();
        Creature newer = new Creature(old.definitionId(), 2, old.name(), 200, 4000, 100, 8, 1600, 0, 0, .1, 2, new GoldDrop(1, 1, 0), List.of());
        var spawn = new Spawn(newer.definitionId(), 2, 3);
        var encounter = new Encounter(pinned.map().encounter(1).encounterId(), "Three Trolls", false, List.of(spawn));
        var newerMap = new io.tiagovibeson.heroassociation.contract.WorldContract.MapDefinition(FIELD, 2, "Troll Field", MapKind.FIELD,
                List.of(new Floor(1, "Broken Pass", "OPEN_FIELD", List.of(encounter))));
        String creatureJson = mapper.writeValueAsString(newer);
        String mapJson = mapper.writeValueAsString(newerMap);
        UUID creatureRow = UUID.fromString("019c4c00-0005-7000-8000-000000000102");
        UUID mapRow = UUID.fromString("019c4c00-0006-7000-8000-000000000102");
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                var creature = new CreatureDefinition(); creature.id = creatureRow; creature.definitionId = newer.definitionId(); creature.version = 2; creature.payload = creatureJson; em.persist(creature);
                var map = new MapDefinition(); map.id = mapRow; map.definitionId = FIELD; map.version = 2; map.payload = mapJson; em.persist(map);
            });
            assertEquals(2, catalog.plan(FIELD, null).map().version());
            assertEquals(4000, catalog.plan(FIELD, null).creatures().getFirst().maxHealth());
            assertEquals(pinned, catalog.plan(FIELD, 1));
            assertEquals(2000, pinned.creatures().getFirst().maxHealth());
        } finally {
            QuarkusTransaction.requiringNew().run(() -> { em.remove(em.find(MapDefinition.class, mapRow)); em.remove(em.find(CreatureDefinition.class, creatureRow)); });
        }
    }

    @Test void bothCreatureSeedsHaveIndependentGoldSevenRunesAndTwoMaterialDrops() {
        Plan plan = catalog.plan(UUID.fromString("019c4c00-0006-7000-8000-000000000002"), 1);
        assertEquals(2, plan.creatures().size());
        for (Creature creature : plan.creatures()) {
            assertEquals(new GoldDrop(1, 25, .5), creature.goldDrop());
            var expected = new ArrayList<Drop>();
            for (int number = 1; number <= 7; number++) expected.add(new Drop("RUNE",
                    UUID.fromString("019c4c00-0020-7000-8000-%012d".formatted(number)), 1, 1, .01));
            expected.add(new Drop("ITEM", UUID.fromString("019c4c00-0070-7000-8000-000000000002"), 1, 5, .05));
            expected.add(new Drop("ITEM", UUID.fromString("019c4c00-0070-7000-8000-000000000001"), 1, 5, .05));
            assertEquals(expected, creature.drops());
        }
    }

    @Test void aDungeonHasFiniteOrderedFloors() {
        Plan field = catalog.plan(FIELD, 1);
        Encounter first = field.map().encounter(1);
        var boss = new Encounter(UUID.fromString("019c4c00-0006-7000-8000-000000000202"), "Boss", true, first.spawns());
        var dungeon = new io.tiagovibeson.heroassociation.contract.WorldContract.MapDefinition(FIELD, 1, "Dungeon", MapKind.DUNGEON,
                List.of(new Floor(1, "Entrance", "LINEAR", List.of(first)), new Floor(2, "Boss chamber", "LINEAR", List.of(boss))));
        assertEquals(1, dungeon.floorNumber(1)); assertEquals(2, dungeon.floorNumber(2));
        assertFalse(dungeon.completedAfter(1)); assertTrue(dungeon.completedAfter(2));
        assertThrows(IllegalArgumentException.class, () -> dungeon.encounter(3));
    }

    @Test void invalidAndMissingVersionsFailClosed() {
        Plan pinned = catalog.plan(FIELD, 1);
        assertThrows(IllegalArgumentException.class, () -> new Plan(pinned.map(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Spawn(FIELD, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new Drop("GOLD", FIELD, 1, 1, 1));
        given().header("X-Hero-Association-World-Service-Key", KEY).get("/internal/v1/world/maps/" + FIELD + "/999").then().statusCode(404);
    }

    @Test void thePrivateCatalogRequiresItsServiceCredential() {
        given().get("/internal/v1/world/maps/" + FIELD).then().statusCode(403);
        given().header("X-Hero-Association-World-Service-Key", KEY).get("/internal/v1/world/maps/" + FIELD).then().statusCode(200);
    }

    @Test @TestSecurity(user = "player") void thePlayerCatalogExposesMapChoices() {
        given().get("/api/v1/maps").then().statusCode(200);
        given().get("/api/v1/creatures").then().statusCode(200);
    }
}
