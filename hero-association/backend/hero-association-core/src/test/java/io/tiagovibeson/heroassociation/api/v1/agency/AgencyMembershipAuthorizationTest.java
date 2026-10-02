package io.tiagovibeson.heroassociation.api.v1.agency;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "local-seed-soren")
class AgencyMembershipAuthorizationTest {

    private static final String DAWNWATCH_AGENCY_ID = "019c4c00-0001-7000-8000-000000000001";

    @Test
    void shouldReadAgencyStateAndRetireTheCoreMarketWriter() {
        given()
                .when().get("/api/v1/agencies/%s/state".formatted(DAWNWATCH_AGENCY_ID))
                .then()
                .statusCode(200)
                .body("agency.name", is("Dawnwatch Agency"));

        given().contentType(ContentType.JSON).body("{}")
                .when().post("/api/v1/market/orders").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "agency-creator")
    void shouldCreateAnEmptyLevelOneAgencyForAManagerWithoutMembership() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"displayName\":\"Agency Creator\"}")
                .when().post("/api/v1/account/manager")
                .then()
                .statusCode(200);

        String agencyId = given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Wayfinder Guild\"}")
                .when().post("/api/v1/agencies")
                .then()
                .statusCode(201)
                .body("agency.name", is("Wayfinder Guild"))
                .body("agency.leaderName", is("Agency Creator"))
                .body("agency.gold", is(0))
                .body("agency.reputation", is(0))
                .body("agency.levels.agency", is(1))
                .body("agency.levels.training", is(1))
                .body("agency.levels.rest", is(1))
                .body("agency.levels.size", is(1))
                .body("agency.levels.reputation", is(1))
                .body("agency.levels.intelligence", is(1))
                .body("heroes.size()", is(0))
                .body("parties.size()", is(1))
                .body("parties[0].name", is("Main Party"))
                .body("parties[0].heroIds.size()", is(3))
                .body("quests.size()", is(0))
                .body("runeInventory.size()", is(0))
                .body("itemInventory.size()", is(0))
                .body("feedPosts.size()", is(0))
                .extract().path("agency.id");

        assertEquals(7, UUID.fromString(agencyId).version());

        given()
                .when().get("/api/v1/agencies/%s/state".formatted(agencyId))
                .then()
                .statusCode(200)
                .body("agency.id", is(agencyId));

        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Second Guild\"}")
                .when().post("/api/v1/agencies")
                .then()
                .statusCode(409)
                .body("message", is("Leave your current agency before creating another one."));
    }

    @Test
    @TestSecurity(user = "agency-name-validator")
    void shouldValidateAgencyNameAndRejectCaseInsensitiveDuplicates() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"displayName\":\"Name Validator\"}")
                .when().post("/api/v1/account/manager")
                .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"  \"}")
                .when().post("/api/v1/agencies")
                .then()
                .statusCode(400)
                .body("message", is("An agency name must contain between 3 and 100 characters."));

        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"dAwNwAtCh aGeNcY\"}")
                .when().post("/api/v1/agencies")
                .then()
                .statusCode(409)
                .body("message", is("The agency name dAwNwAtCh aGeNcY is already in use."));
    }

    @Test
    @TestSecurity(user = "unassigned-manager")
    void shouldRejectAManagerWhoDoesNotBelongToTheAgency() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"displayName\":\"Unassigned Ranger\"}")
                .when().post("/api/v1/account/manager")
                .then()
                .statusCode(200);

        given()
                .when().get("/api/v1/agencies/%s/state".formatted(DAWNWATCH_AGENCY_ID))
                .then()
                .statusCode(403)
                .body("message", is("You are not a member of this agency."));
    }
}
