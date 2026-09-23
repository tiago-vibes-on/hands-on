package io.tiagovibeson.heroassociation.api.v1.agency;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
class PartyManagementControllerTest {

    private static final String AGENCY_ID = "019c4c00-0001-7000-8000-000000000001";
    private static final String BROKEN_PASS_PARTY_ID = "019c4c00-0002-7000-8000-000000000001";
    private static final String IRONWALL_ID = "019c4c00-0010-7000-8000-000000000001";
    private static final String OAKSHIELD_ID = "019c4c00-0010-7000-8000-000000000004";

    @Test
    void shouldCreateAPreparedPartyAndManageItsMembers() {
        String partyId = createParty("Forest Scouts")
                .then()
                .statusCode(200)
                .body("parties.name", hasItem("Forest Scouts"))
                .extract()
                .path("parties.find { it.name == 'Forest Scouts' }.id");

        given()
                .when()
                .put("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, partyId, OAKSHIELD_ID))
                .then()
                .statusCode(200)
                .body("heroes.find { it.id == '%s' }.partyId".formatted(OAKSHIELD_ID), is(partyId))
                .body("heroes.find { it.id == '%s' }.activity".formatted(OAKSHIELD_ID), is("TRAINING"))
                .body("parties.find { it.id == '%s' }.heroIds".formatted(partyId), hasItem(OAKSHIELD_ID));

        given()
                .when()
                .delete("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, partyId, OAKSHIELD_ID))
                .then()
                .statusCode(200)
                .body("heroes.find { it.id == '%s' }.partyId".formatted(OAKSHIELD_ID), nullValue())
                .body("parties.find { it.id == '%s' }.heroIds".formatted(partyId), empty());
    }

    @Test
    void shouldRejectMembershipChangesForAnActiveQuestParty() {
        given()
                .when()
                .put("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, BROKEN_PASS_PARTY_ID, OAKSHIELD_ID))
                .then()
                .statusCode(409)
                .body("message", is("Party with id %s is on a quest and its membership cannot change.".formatted(BROKEN_PASS_PARTY_ID)));
    }

    @Test
    void shouldRejectMovingAQuestHeroToAPreparedParty() {
        String partyId = createParty("North Watch")
                .then()
                .statusCode(200)
                .extract()
                .path("parties.find { it.name == 'North Watch' }.id");

        given()
                .when()
                .put("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, partyId, IRONWALL_ID))
                .then()
                .statusCode(409)
                .body("message", is("Hero with id %s is on a quest and is not available for this action.".formatted(IRONWALL_ID)));
    }

    @Test
    void shouldRejectDuplicatePartyNamesWithinAnAgency() {
        createParty("Harbor Watch")
                .then()
                .statusCode(200);

        createParty("Harbor Watch")
                .then()
                .statusCode(409)
                .body("message", is("A party named Harbor Watch already exists in this agency."));
    }

    private Response createParty(String name) {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"%s\"}".formatted(name))
                .when()
                .post("/api/v1/agencies/%s/parties".formatted(AGENCY_ID));
    }
}
