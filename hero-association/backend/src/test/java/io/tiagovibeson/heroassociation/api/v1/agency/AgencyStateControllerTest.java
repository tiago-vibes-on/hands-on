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

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AgencyStateControllerTest {

    private static final String DAWNWATCH_AGENCY_ID = "019c4c00-0001-7000-8000-000000000001";
    private static final String TROLL_QUEST_ID = "019c4c00-0003-7000-8000-000000000001";
    private static final String LOST_COURIER_QUEST_ID = "019c4c00-0004-7000-8000-000000000001";
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
                .body("agency.leaderName", is("Tiago"))
                .body("agency.levels.rest", is(3))
                .body("agency.levels.intelligence", is(3))
                .body("agency.levels", not(hasKey("medical")))
                .body("heroes.alias", hasItems("Ironwall", "Moonweaver", "Swiftarrow", "Oakshield", "Emberveil", "Hawkeye"))
                .body("parties.name", hasItems("Broken Pass Party"))
                .body("quests.find { it.id == '%s' }.title".formatted(LOST_COURIER_QUEST_ID), is("Lost Courier"))
                .body("quests.find { it.id == '%s' }.status".formatted(LOST_COURIER_QUEST_ID), is("AVAILABLE"))
                .body("quests.find { it.id == '%s' }.minimumHeroes".formatted(LOST_COURIER_QUEST_ID), is(1))
                .body("quests.find { it.id == '%s' }.maximumHeroes".formatted(LOST_COURIER_QUEST_ID), is(2))
                .body("runeInventory.rune.code", hasItems("attack-rune", "critical-chance-rune", "critical-damage-rune"))
                .body("itemInventory.item.code", hasItems("magic-crystal", "iron-ingot"))
                .body("itemInventory.find { it.item.code == 'magic-crystal' }.quantity", is(3))
                .body("feedPosts.authorType", hasItems("AGENCY", "HERO"));
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
    void shouldExposeThePersistedTrollCombatSnapshot() {
        given()
                .when().get("/api/v1/agencies/" + DAWNWATCH_AGENCY_ID + "/state")
                .then()
                .statusCode(200)
                .body("quests.find { it.id == '%s' }.combat.status".formatted(TROLL_QUEST_ID), is("IN_PROGRESS"))
                .body("quests.find { it.id == '%s' }.combat.currentTimeMilliseconds".formatted(TROLL_QUEST_ID), greaterThanOrEqualTo(0))
                .body("quests.find { it.id == '%s' }.combat.lastSynchronizedAt".formatted(TROLL_QUEST_ID), notNullValue())
                .body("quests.find { it.id == '%s' }.combat.combatants".formatted(TROLL_QUEST_ID), hasSize(6))
                .body("quests.find { it.id == '%s' }.combat.combatants.find { it.name == 'Moonweaver' }.fireBallNextCastAt".formatted(TROLL_QUEST_ID), greaterThanOrEqualTo(900))
                .body("quests.find { it.id == '%s' }.combat.combatants.find { it.name == 'Moonweaver' }.lightningRailNextCastAt".formatted(TROLL_QUEST_ID), greaterThanOrEqualTo(1350))
                .body("quests.find { it.id == '%s' }.combat.combatants.find { it.name == 'Troll' }.criticalChance".formatted(TROLL_QUEST_ID), is(0.1F));
    }

    @Test
    void shouldSynchronizeThePersistedCombatThroughAnExplicitCommand() {
        Number currentTime = given()
                .when().get("/api/v1/agencies/" + DAWNWATCH_AGENCY_ID + "/state")
                .then()
                .statusCode(200)
                .extract()
                .path("quests.find { it.id == '%s' }.combat.currentTimeMilliseconds".formatted(TROLL_QUEST_ID));

        Response synchronizedState = given()
                .when().post("/api/v1/agencies/%s/quests/%s/combat/sync".formatted(DAWNWATCH_AGENCY_ID, TROLL_QUEST_ID))
                .then()
                .statusCode(200)
                .body("quests.find { it.id == '%s' }.combat.currentTimeMilliseconds".formatted(TROLL_QUEST_ID), greaterThan(currentTime.intValue()))
                .body("quests.find { it.id == '%s' }.combat.lastSynchronizedAt".formatted(TROLL_QUEST_ID), notNullValue())
                .body("quests.find { it.id == '%s' }.combat.events.action".formatted(TROLL_QUEST_ID), hasItems("BASIC_ATTACK"))
                .extract()
                .response();

        Number combatHealth = synchronizedState.path("quests.find { it.id == '%s' }.combat.combatants.find { it.name == 'Ironwall' }.currentHealth".formatted(TROLL_QUEST_ID));
        Number heroHealth = synchronizedState.path("heroes.find { it.alias == 'Ironwall' }.currentHealth");
        Number combatMana = synchronizedState.path("quests.find { it.id == '%s' }.combat.combatants.find { it.name == 'Moonweaver' }.currentMana".formatted(TROLL_QUEST_ID));
        Number heroMana = synchronizedState.path("heroes.find { it.alias == 'Moonweaver' }.currentMana");

        assertEquals(combatHealth.intValue(), heroHealth.intValue());
        assertEquals(combatMana.intValue(), heroMana.intValue());
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
