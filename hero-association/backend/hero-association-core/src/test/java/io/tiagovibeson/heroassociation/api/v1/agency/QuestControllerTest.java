package io.tiagovibeson.heroassociation.api.v1.agency;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "local-seed-soren")
class QuestControllerTest {

    private static final String AGENCY_ID = "019c4c00-0001-7000-8000-000000000001";
    private static final String LOST_COURIER_QUEST_ID = "019c4c00-0004-7000-8000-000000000001";
    private static final String PERSONAL_WARRIOR_ID = "019c4c00-0030-7001-8000-000000000003";
    private static final String PERSONAL_MAGE_ID = "019c4c00-0030-7002-8000-000000000003";
    private static final String PERSONAL_ARCHER_ID = "019c4c00-0030-7003-8000-000000000003";

    @Test
    void shouldRejectStartingAnotherManagersParty() {
        startQuest("019c4c00-0002-7000-8000-000000000001")
                .then()
                .statusCode(404);
    }

    @Test
    void shouldStartAnAvailableQuestWithAnEligiblePreparedParty() {
        String partyId = "019c4c00-0002-7001-8000-000000000003";

        startQuest(partyId)
                .then()
                .statusCode(400)
                .body("message", is("Party must contain between 1 and 2 heroes to start this quest."));

        given()
                .when()
                .delete("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, partyId, PERSONAL_MAGE_ID))
                .then()
                .statusCode(200);
        given()
                .when()
                .delete("/api/v1/agencies/%s/parties/%s/heroes/%s".formatted(AGENCY_ID, partyId, PERSONAL_ARCHER_ID))
                .then()
                .statusCode(200);

        startQuest(partyId)
                .then()
                .statusCode(200)
                .body("quests.find { it.id == '%s' }.status".formatted(LOST_COURIER_QUEST_ID), is("IN_PROGRESS"))
                .body("quests.find { it.id == '%s' }.partyId".formatted(LOST_COURIER_QUEST_ID), is(partyId))
                .body("parties.find { it.id == '%s' }.quest.title".formatted(partyId), is("Lost Courier"))
                .body("quests.find { it.id == '%s' }.startedAt".formatted(LOST_COURIER_QUEST_ID), notNullValue())
                .body("quests.find { it.id == '%s' }.combat.status".formatted(LOST_COURIER_QUEST_ID), is("IN_PROGRESS"))
                .body("quests.find { it.id == '%s' }.combat.currentTimeMilliseconds".formatted(LOST_COURIER_QUEST_ID), is(0))
                .body("quests.find { it.id == '%s' }.combat.combatants".formatted(LOST_COURIER_QUEST_ID), hasSize(5))
                .body("quests.find { it.id == '%s' }.combat.combatants.find { it.team == 'HEROES' }.heroId".formatted(LOST_COURIER_QUEST_ID), is(PERSONAL_WARRIOR_ID))
                .body("quests.find { it.id == '%s' }.combat.combatants.find { it.team == 'CREATURES' }.name".formatted(LOST_COURIER_QUEST_ID), is("Forest Wolf"))
                .body("quests.find { it.id == '%s' }.combat.combatants.find { it.team == 'CREATURES' }.maxHealth".formatted(LOST_COURIER_QUEST_ID), is(120))
                .body("quests.find { it.id == '%s' }.expectedCompletionAt".formatted(LOST_COURIER_QUEST_ID), notNullValue())
                .body("personalHeroes.find { it.id == '%s' }.activity".formatted(PERSONAL_WARRIOR_ID), is("ON_QUEST"));

        startQuest(partyId)
                .then()
                .statusCode(409)
                .body("message", is("Quest with id %s is not available to start.".formatted(LOST_COURIER_QUEST_ID)));
    }


    private Response startQuest(String partyId) {
        return startQuest(partyId, 0);
    }

    private Response startQuest(String partyId, long expectedBorrowingFeeGold) {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"partyId\":\"%s\",\"expectedBorrowingFeeGold\":%d,\"operationKey\":\"%s\"}"
                        .formatted(partyId, expectedBorrowingFeeGold, io.tiagovibeson.heroassociation.domain.UuidV7.next()))
                .when()
                .put("/api/v1/agencies/%s/quests/%s/start".formatted(AGENCY_ID, LOST_COURIER_QUEST_ID));
    }
}
