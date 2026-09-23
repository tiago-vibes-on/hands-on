package io.tiagovibeson.heroassociation.api.v1.agency;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
class RuneLoadoutControllerTest {

    private static final String AGENCY_ID = "019c4c00-0001-7000-8000-000000000001";
    private static final String IRONWALL_ID = "019c4c00-0010-7000-8000-000000000001";
    private static final String OAKSHIELD_ID = "019c4c00-0010-7000-8000-000000000004";
    private static final String ATTACK_RUNE_ID = "019c4c00-0020-7000-8000-000000000001";
    private static final String VITALITY_RUNE_ID = "019c4c00-0020-7000-8000-000000000003";
    private static final String CRITICAL_CHANCE_RUNE_ID = "019c4c00-0020-7000-8000-000000000006";

    @Test
    void shouldMoveRunesAtomicallyBetweenInventoryAndHeroSlots() {
        equip(OAKSHIELD_ID, 0, ATTACK_RUNE_ID)
                .then()
                .statusCode(200)
                .body("heroes.find { it.alias == 'Oakshield' }.runeSlots[0].rune.code", is("attack-rune"))
                .body("runeInventory.find { it.rune.code == 'attack-rune' }.quantity", is(0));

        unequip(OAKSHIELD_ID, 0)
                .then()
                .statusCode(200)
                .body("heroes.find { it.alias == 'Oakshield' }.runeSlots[0].rune", nullValue())
                .body("runeInventory.find { it.rune.code == 'attack-rune' }.quantity", is(1));

        equip(IRONWALL_ID, 0, VITALITY_RUNE_ID)
                .then()
                .statusCode(200)
                .body("heroes.find { it.alias == 'Ironwall' }.runeSlots[0].rune.code", is("vitality-rune"))
                .body("runeInventory.find { it.rune.code == 'attack-rune' }.quantity", is(2))
                .body("runeInventory.find { it.rune.code == 'vitality-rune' }.quantity", is(0));

        equip(IRONWALL_ID, 0, ATTACK_RUNE_ID)
                .then()
                .statusCode(200)
                .body("heroes.find { it.alias == 'Ironwall' }.runeSlots[0].rune.code", is("attack-rune"))
                .body("runeInventory.find { it.rune.code == 'attack-rune' }.quantity", is(1))
                .body("runeInventory.find { it.rune.code == 'vitality-rune' }.quantity", is(1));
    }

    @Test
    void shouldRejectARuneThatIsNotAvailableInTheAgencyInventory() {
        equip(OAKSHIELD_ID, 0, CRITICAL_CHANCE_RUNE_ID)
                .then()
                .statusCode(409)
                .body("message", is("Rune with id %s is not available in this agency inventory.".formatted(CRITICAL_CHANCE_RUNE_ID)));
    }

    @Test
    void shouldRejectAnInvalidRuneSlot() {
        equip(OAKSHIELD_ID, 5, ATTACK_RUNE_ID)
                .then()
                .statusCode(400)
                .body("message", is("Rune slot 5 is invalid. A hero has slots 0 through 4."));
    }

    private io.restassured.response.Response equip(String heroId, int slotIndex, String runeId) {
        return given()
                .contentType(ContentType.JSON)
                .body(new EquipRuneRequest(java.util.UUID.fromString(runeId)))
                .when()
                .put(slotPath(heroId, slotIndex));
    }

    private io.restassured.response.Response unequip(String heroId, int slotIndex) {
        return given()
                .when()
                .delete(slotPath(heroId, slotIndex));
    }

    private String slotPath(String heroId, int slotIndex) {
        return "/api/v1/agencies/%s/heroes/%s/rune-slots/%d".formatted(AGENCY_ID, heroId, slotIndex);
    }
}
