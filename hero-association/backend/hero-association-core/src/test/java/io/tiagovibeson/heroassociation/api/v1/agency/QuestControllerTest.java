package io.tiagovibeson.heroassociation.api.v1.agency;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
class QuestControllerTest {

    private static final String AGENCY_ID = "019c4c00-0001-7000-8000-000000000001";
    private static final String LOST_COURIER_QUEST_ID = "019c4c00-0004-7000-8000-000000000001";
    private static final String OAKSHIELD_ID = "019c4c00-0010-7000-8000-000000000004";
    private static final String EMBERVEIL_ID = "019c4c00-0010-7000-8000-000000000005";
    private static final String HAWKEYE_ID = "019c4c00-0010-7000-8000-000000000006";

    @Test
    void shouldStartAnAvailableQuestWithAnEligiblePreparedParty() {
        String oversizedPartyId = createParty("Forest Vanguard")
                .then()
                .statusCode(200)
                .extract()
                .path("parties.find { it.name == 'Forest Vanguard' }.id");
        addHero(oversizedPartyId, OAKSHIELD_ID);
        addHero(oversizedPartyId, EMBERVEIL_ID);
        addHero(oversizedPartyId, HAWKEYE_ID);

        startQuest(oversizedPartyId)
                .then()
                .statusCode(400)
                .body("message", is("Party must contain between 1 and 2 heroes to start this quest."));

        given()
                .when()
                .delete("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, oversizedPartyId, OAKSHIELD_ID))
                .then()
                .statusCode(200);

        String courierPartyId = createParty("Courier Scouts")
                .then()
                .statusCode(200)
                .extract()
                .path("parties.find { it.name == 'Courier Scouts' }.id");
        addHero(courierPartyId, OAKSHIELD_ID);

        startQuest(courierPartyId)
                .then()
                .statusCode(200)
                .body("quests.find { it.id == '%s' }.status".formatted(LOST_COURIER_QUEST_ID), is("IN_PROGRESS"))
                .body("quests.find { it.id == '%s' }.partyId".formatted(LOST_COURIER_QUEST_ID), is(courierPartyId))
                .body("parties.find { it.id == '%s' }.quest.title".formatted(courierPartyId), is("Lost Courier"))
                .body("quests.find { it.id == '%s' }.startedAt".formatted(LOST_COURIER_QUEST_ID), notNullValue())
                .body("quests.find { it.id == '%s' }.expectedCompletionAt".formatted(LOST_COURIER_QUEST_ID), notNullValue())
                .body("heroes.find { it.id == '%s' }.activity".formatted(OAKSHIELD_ID), is("ON_QUEST"));

        startQuest(courierPartyId)
                .then()
                .statusCode(409)
                .body("message", is("Quest with id %s is not available to start.".formatted(LOST_COURIER_QUEST_ID)));
    }

    private Response createParty(String name) {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"%s\"}".formatted(name))
                .when()
                .post("/api/v1/agencies/%s/parties".formatted(AGENCY_ID));
    }

    private void addHero(String partyId, String heroId) {
        given()
                .when()
                .put("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, partyId, heroId))
                .then()
                .statusCode(200);
    }

    private Response startQuest(String partyId) {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"partyId\":\"%s\"}".formatted(partyId))
                .when()
                .put("/api/v1/agencies/%s/quests/%s/start".formatted(AGENCY_ID, LOST_COURIER_QUEST_ID));
    }
}
