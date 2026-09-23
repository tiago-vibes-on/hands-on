package io.tiagovibeson.heroassociation.api.v1.agency;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
class HeroActivityControllerTest {

    private static final String AGENCY_ID = "019c4c00-0001-7000-8000-000000000001";
    private static final String IRONWALL_ID = "019c4c00-0010-7000-8000-000000000001";
    private static final String OAKSHIELD_ID = "019c4c00-0010-7000-8000-000000000004";

    @Test
    void shouldChangeAnAgencyHeroBetweenTrainingAndResting() {
        changeActivity(OAKSHIELD_ID, "RESTING")
                .then()
                .statusCode(200)
                .body("heroes.find { it.alias == 'Oakshield' }.activity", is("RESTING"));

        changeActivity(OAKSHIELD_ID, "TRAINING")
                .then()
                .statusCode(200)
                .body("heroes.find { it.alias == 'Oakshield' }.activity", is("TRAINING"));
    }

    @Test
    void shouldRejectChangingTheActivityOfAHeroOnAQuest() {
        changeActivity(IRONWALL_ID, "RESTING")
                .then()
                .statusCode(409)
                .body("message", is("Hero with id %s is on a quest and is not available for this action.".formatted(IRONWALL_ID)));
    }

    @Test
    void shouldRejectAssigningQuestActivityWithoutAParty() {
        changeActivity(OAKSHIELD_ID, "ON_QUEST")
                .then()
                .statusCode(400)
                .body("message", is("A hero activity can only be TRAINING or RESTING outside a quest."));
    }

    private io.restassured.response.Response changeActivity(String heroId, String activity) {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"activity\":\"%s\"}".formatted(activity))
                .when()
                .put("/api/v1/agencies/%s/heroes/%s/activity".formatted(AGENCY_ID, heroId));
    }
}
