package io.tiagovibeson.heroassociation.api.v1.agency;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
class AgencyStateControllerTest {

    private static final String DAWNWATCH_AGENCY_ID = "019c4c00-0001-7000-8000-000000000001";
    private static final String MOONWEAVER_HERO_ID = "019c4c00-0010-7000-8000-000000000002";
    private static final String UNKNOWN_AGENCY_ID = "019c4c00-ffff-7fff-8fff-ffffffffffff";

    @Test
    void shouldExposeTheSeededAgencyGameState() {
        given()
                .when().get("/api/v1/agencies/" + DAWNWATCH_AGENCY_ID + "/state")
                .then()
                .statusCode(200)
                .body("agency.id", is(DAWNWATCH_AGENCY_ID))
                .body("agency.name", is("Dawnwatch Agency"))
                .body("agency.leaderName", is("User 1"))
                .body("agency.levels.rest", is(3))
                .body("agency.levels.intelligence", is(3))
                .body("agency.levels", not(hasKey("medical")))
                .body("heroes.alias", hasItems("Ironwall", "Moonweaver", "Swiftarrow", "Oakshield", "Emberveil", "Hawkeye"))
                .body("parties.name", hasItems("Broken Pass Party"))
                .body("runeInventory.rune.code", hasItems("attack-rune", "critical-chance-rune", "critical-damage-rune"))
                .body("itemInventory.item.code", hasItems("magic-crystal", "iron-ingot"))
                .body("itemInventory.find { it.item.code == 'magic-crystal' }.quantity", is(3))
                .body("feedPosts.authorType", hasItems("AGENCY", "HERO"));
    }

    @Test
    void shouldExposeClassRecoveryAndTheHeroLoadout() {
        given()
                .when().get("/api/v1/agencies/" + DAWNWATCH_AGENCY_ID + "/state")
                .then()
                .statusCode(200)
                .body("heroes.find { it.alias == 'Moonweaver' }.magicLevel", is(15))
                .body("heroes.find { it.alias == 'Moonweaver' }.experience", notNullValue())
                .body("heroes.find { it.alias == 'Moonweaver' }.healthRecoveryPerSecond", is(2))
                .body("heroes.find { it.alias == 'Moonweaver' }.manaRecoveryPerSecond", is(10))
                .body("heroes.find { it.alias == 'Moonweaver' }.runeSlots[1].rune.code", is("critical-chance-rune"))
                .body("$", not(hasKey("quests")))
                .body("parties[0]", not(hasKey("quest")));
    }

    @Test
    void shouldCreateAPostForAnAgencyHero() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "authorType": "HERO",
                          "authorId": "%s",
                          "content": "Broken Pass is clear enough to keep moving.",
                          "itemId": "019c4c00-0070-7000-8000-000000000001",
                          "itemQuantity": 2
                        }
                        """.formatted(MOONWEAVER_HERO_ID))
                .when().post("/api/v1/agencies/%s/feed-posts".formatted(DAWNWATCH_AGENCY_ID))
                .then()
                .statusCode(200)
                .body("feedPosts.find { it.content == 'Broken Pass is clear enough to keep moving.' }.authorType", is("HERO"))
                .body("feedPosts.find { it.content == 'Broken Pass is clear enough to keep moving.' }.authorName", is("Elara Moonweaver"))
                .body("feedPosts.find { it.content == 'Broken Pass is clear enough to keep moving.' }.item.code", is("magic-crystal"))
                .body("feedPosts.find { it.content == 'Broken Pass is clear enough to keep moving.' }.itemQuantity", is(2))
                .body("itemInventory.find { it.item.code == 'magic-crystal' }.quantity", is(3));
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
