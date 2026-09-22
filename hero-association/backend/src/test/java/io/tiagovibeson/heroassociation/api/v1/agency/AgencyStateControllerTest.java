package io.tiagovibeson.heroassociation.api.v1.agency;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AgencyStateControllerTest {

    private static final String DAWNWATCH_AGENCY_ID = "019c4c00-0001-7000-8000-000000000001";
    private static final String UNKNOWN_AGENCY_ID = "019c4c00-ffff-7fff-8fff-ffffffffffff";

    @Test
    void shouldExposeTheSeededAgencyGameState() {
        given()
                .when().get("/api/v1/agencies/" + DAWNWATCH_AGENCY_ID + "/state")
                .then()
                .statusCode(200)
                .body("agency.id", is(DAWNWATCH_AGENCY_ID))
                .body("agency.name", is("Dawnwatch Agency"))
                .body("agency.leaderName", is("Tiago"))
                .body("agency.levels.rest", is(3))
                .body("agency.levels.intelligence", is(3))
                .body("agency.levels", not(hasKey("medical")))
                .body("heroes.alias", hasItems("Ironwall", "Moonweaver", "Swiftarrow", "Oakshield", "Emberveil", "Hawkeye"))
                .body("parties.name", hasItems("Broken Pass Party"))
                .body("runeInventory.rune.code", hasItems("attack-rune", "critical-chance-rune", "critical-damage-rune"));
    }

    @Test
    void shouldExposeClassRecoveryAndTheQuestLoadout() {
        given()
                .when().get("/api/v1/agencies/" + DAWNWATCH_AGENCY_ID + "/state")
                .then()
                .statusCode(200)
                .body("heroes.find { it.alias == 'Moonweaver' }.magicLevel", is(15))
                .body("heroes.find { it.alias == 'Moonweaver' }.healthRecoveryPerSecond", is(2))
                .body("heroes.find { it.alias == 'Moonweaver' }.manaRecoveryPerSecond", is(10))
                .body("heroes.find { it.alias == 'Moonweaver' }.runeSlots[1].rune.code", is("critical-chance-rune"))
                .body("parties[0].quest.title", is("Trolls at Broken Pass"));
    }

    @Test
    void shouldReturnNotFoundForAnUnknownAgency() {
        given()
                .when().get("/api/v1/agencies/" + UNKNOWN_AGENCY_ID + "/state")
                .then()
                .statusCode(404)
                .body("message", is("Agency with id %s was not found.".formatted(UNKNOWN_AGENCY_ID)));
    }
}
