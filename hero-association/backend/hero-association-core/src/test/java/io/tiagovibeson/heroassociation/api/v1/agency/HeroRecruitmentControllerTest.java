package io.tiagovibeson.heroassociation.api.v1.agency;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItems;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "recruiter")
class HeroRecruitmentControllerTest {

    private static final String STEELWARD_RECRUIT_ID = "019c4c00-0010-7000-8000-000000000007";

    @Test
    void shouldListAndRecruitAnAvailableHeroThenExposeItsDetails() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"displayName\":\"Recruiter\"}")
                .when().post("/api/v1/account/manager")
                .then()
                .statusCode(200);

        given()
                .when().get("/api/v1/recruits")
                .then()
                .statusCode(200)
                .body("alias", hasItems("Steelward", "Dawnflame", "Windmark"))
                .body("heroClass", hasItems("WARRIOR", "MAGE", "ARCHER"));

        String agencyId = given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Recruitment Guild\"}")
                .when().post("/api/v1/agencies")
                .then()
                .statusCode(201)
                .extract().path("agency.id");

        given()
                .contentType(ContentType.JSON)
                .body("{\"recruitId\":\"%s\"}".formatted(STEELWARD_RECRUIT_ID))
                .when().post("/api/v1/agencies/%s/heroes".formatted(agencyId))
                .then()
                .statusCode(200)
                .body("agency.gold", is(0))
                .body("heroes.size()", is(1))
                .body("heroes[0].id", is(STEELWARD_RECRUIT_ID))
                .body("heroes[0].alias", is("Steelward"))
                .body("heroes[0].heroClass", is("WARRIOR"))
                .body("heroes[0].level", is(1))
                .body("heroes[0].activity", is("TRAINING"))
                .body("heroes[0].currentHealth", is(300))
                .body("heroes[0].currentMana", is(50))
                .body("heroes[0].stamina", is(100));

        given()
                .when().get("/api/v1/agencies/%s/heroes/%s".formatted(agencyId, STEELWARD_RECRUIT_ID))
                .then()
                .statusCode(200)
                .body("name", is("Alden Steelward"))
                .body("healthRecoveryPerSecond", is(10))
                .body("manaRecoveryPerSecond", is(2))
                .body("partyId", is(org.hamcrest.Matchers.nullValue()));

        given()
                .when().get("/api/v1/recruits")
                .then()
                .statusCode(200)
                .body("size()", is(2));

        given()
                .contentType(ContentType.JSON)
                .body("{\"recruitId\":\"%s\"}".formatted(STEELWARD_RECRUIT_ID))
                .when().post("/api/v1/agencies/%s/heroes".formatted(agencyId))
                .then()
                .statusCode(409)
                .body("message", is("Recruit with id %s is no longer available.".formatted(STEELWARD_RECRUIT_ID)));
    }
}
