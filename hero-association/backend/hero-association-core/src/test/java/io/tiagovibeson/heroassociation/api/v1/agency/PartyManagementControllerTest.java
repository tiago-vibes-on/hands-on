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
    private static final String PERSONAL_WARRIOR_ID = "019c4c00-0030-7001-8000-000000000001";
    private static final String OTHER_PERSONAL_WARRIOR_ID = "019c4c00-0030-7001-8000-000000000201";

    @Test
    void shouldCreateAPreparedPartyAndManageItsMembers() {
        String partyId = createParty("Forest Scouts")
                .then()
                .statusCode(200)
                .body("parties.name", hasItem("Forest Scouts"))
                .body("parties.find { it.name == 'Forest Scouts' }.ownerManagerId",
                        is("019c4c00-0000-7000-8000-000000000001"))
                .extract()
                .path("parties.find { it.name == 'Forest Scouts' }.id");

        given()
                .when()
                .put("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, partyId, PERSONAL_WARRIOR_ID))
                .then()
                .statusCode(200)
                .body("personalHeroes.find { it.id == '%s' }.partyId".formatted(PERSONAL_WARRIOR_ID), is(partyId))
                .body("personalHeroes.find { it.id == '%s' }.activity".formatted(PERSONAL_WARRIOR_ID), is("TRAINING"))
                .body("parties.find { it.id == '%s' }.heroIds".formatted(partyId), hasItem(PERSONAL_WARRIOR_ID));

        given()
                .when()
                .delete("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, partyId, PERSONAL_WARRIOR_ID))
                .then()
                .statusCode(200)
                .body("personalHeroes.find { it.id == '%s' }.partyId".formatted(PERSONAL_WARRIOR_ID), nullValue())
                .body("parties.find { it.id == '%s' }.heroIds".formatted(partyId), empty());
    }

    @jakarta.inject.Inject io.tiagovibeson.heroassociation.application.expedition.ExpeditionAdmissionService admission;
    @Test
    void shouldRejectMembershipChangesForAnActiveExpeditionParty() {
        String party = createParty("Active expedition test").then().statusCode(200).extract().path("parties.find { it.name == 'Active expedition test' }.id");
        given().put("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, party, PERSONAL_WARRIOR_ID)).then().statusCode(200);
        var run = io.tiagovibeson.heroassociation.domain.UuidV7.next();
        var manager = java.util.UUID.fromString("019c4c00-0000-7000-8000-000000000001");
        admission.reserve(run, manager, java.util.UUID.fromString(AGENCY_ID), java.util.UUID.fromString(party));
        try {
            given().put("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, party, OAKSHIELD_ID)).then().statusCode(409);
            given().delete("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, party, PERSONAL_WARRIOR_ID)).then().statusCode(409);
        } finally {
            admission.releaseProvenAbsent(run, manager);
            given().delete("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, party, PERSONAL_WARRIOR_ID)).then().statusCode(200);
        }
    }

    @Test
    void shouldBorrowAnAvailableAgencyHeroWithoutChargingAtAssignment() {
        String partyId = createParty("North Watch")
                .then()
                .statusCode(200)
                .extract()
                .path("parties.find { it.name == 'North Watch' }.id");

        given()
                .when()
                .put("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, partyId, OAKSHIELD_ID))
                .then()
                .statusCode(200)
                .body("heroes.find { it.id == '%s' }.partyId".formatted(OAKSHIELD_ID), is(partyId))
                .body("heroes.find { it.id == '%s' }.borrowingFeeGold".formatted(OAKSHIELD_ID), is(0))
                .body("agency.gold", is(2480));

        String otherPartyId = createParty("South Watch")
                .then().statusCode(200)
                .extract().path("parties.find { it.name == 'South Watch' }.id");

        given()
                .when()
                .put("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, otherPartyId, OAKSHIELD_ID))
                .then()
                .statusCode(409)
                .body("message", is("Hero with id %s is already assigned to another party.".formatted(OAKSHIELD_ID)));

        given()
                .when()
                .delete("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, partyId, OAKSHIELD_ID))
                .then().statusCode(200)
                .body("heroes.find { it.id == '%s' }.partyId".formatted(OAKSHIELD_ID), nullValue());
    }

    @Test
    void shouldRejectAssigningAnotherManagersPersonalHero() {
        String partyId = createParty("Owner Only")
                .then().statusCode(200)
                .extract().path("parties.find { it.name == 'Owner Only' }.id");

        given()
                .when()
                .put("/api/v1/agencies/%s/parties/%s/heroes/%s"
                        .formatted(AGENCY_ID, partyId, OTHER_PERSONAL_WARRIOR_ID))
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000101")
    void shouldRejectChangingAnotherManagersParty() {
        given()
                .when()
                .put("/api/v1/agencies/%s/parties/%s/heroes/%s"
                        .formatted(AGENCY_ID, BROKEN_PASS_PARTY_ID, OTHER_PERSONAL_WARRIOR_ID))
                .then()
                .statusCode(404);

        given()
                .when()
                .delete("/api/v1/agencies/%s/parties/%s/heroes/%s"
                        .formatted(AGENCY_ID, BROKEN_PASS_PARTY_ID, IRONWALL_ID))
                .then()
                .statusCode(404);
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
