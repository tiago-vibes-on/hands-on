package io.tiagovibeson.heroassociation.bff.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;

import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.tiagovibeson.heroassociation.bff.testsupport.GameCoreStubResource;

@QuarkusTest
@QuarkusTestResource(GameCoreStubResource.class)
@TestSecurity(user = "test-player")
class GameCoreProxyResourceTest {

    @Test
    void shouldForwardGetRequestsToGameCore() {
        given()
                .when().get("/api/v1/echo?source=bff")
                .then()
                .statusCode(200)
                .body("method", equalTo("GET"))
                .body("query", equalTo("source=bff"))
                .body("authorization", equalTo("Bearer test-access-token"))
                .body("traceparent", matchesPattern("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}"));
    }

    @Test
    void shouldForwardBodylessRecruitClaimToGameCore() {
        Response session = given()
                .when().get("/api/v1/session")
                .then()
                .statusCode(200)
                .extract().response();

        given()
                .cookie("hero-association-csrf", session.getCookie("hero-association-csrf"))
                .header("X-CSRF-TOKEN", session.jsonPath().getString("csrfToken"))
                .when().post("/api/v1/recruits/019c4c00-0010-7000-8000-000000000007/claim")
                .then()
                .statusCode(200)
                .body("method", equalTo("POST"))
                .body("authorization", equalTo("Bearer test-access-token"))
                .body("body", equalTo(""));
    }

    @Test
    void shouldForwardWriteRequestsAndJsonBodiesToGameCore() {
        Response session = given()
                .when().get("/api/v1/session")
                .then()
                .statusCode(200)
                .extract().response();
        String csrfToken = session.jsonPath().getString("csrfToken");

        given()
                .cookie("hero-association-csrf", session.getCookie("hero-association-csrf"))
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType("application/json")
                .body("{\"name\":\"Dawnwatch\"}")
                .when().post("/api/v1/echo")
                .then()
                .statusCode(200)
                .body("method", equalTo("POST"))
                .body("contentType", equalTo("application/json"))
                .body("body", equalTo("{\"name\":\"Dawnwatch\"}"));
    }
    @Test
    void shouldRouteExpeditionReadsToExpedition() {
        given()
                .when().get("/api/v1/expeditions/active")
                .then()
                .statusCode(200)
                .body("path", equalTo("/expedition/api/v1/expeditions/active"))
                .body("authorization", equalTo("Bearer test-access-token"));
    }

    @Test
    void shouldProtectAndForwardExpeditionCommands() {
        Response session = given().when().get("/api/v1/session").then()
                .statusCode(200).extract().response();
        given()
                .cookie("hero-association-csrf", session.getCookie("hero-association-csrf"))
                .header("X-CSRF-TOKEN", session.jsonPath().getString("csrfToken"))
                .contentType("application/json")
                .body("{\"commandId\":\"019c4c00-0007-7000-8000-000000000001\",\"expectedVersion\":2}")
                .when().post("/api/v1/expeditions/019c4c00-0007-7000-8000-000000000002/continue")
                .then()
                .statusCode(200)
                .body("path", equalTo("/expedition/api/v1/expeditions/019c4c00-0007-7000-8000-000000000002/continue"))
                .body("authorization", equalTo("Bearer test-access-token"))
                .body("method", equalTo("POST"));
    }
    @Test void worldCatalogAndQuestCommandsReachTheirOwners() {
        given().get("/api/v1/maps").then().statusCode(200).body("path", equalTo("/world/api/v1/maps")).body("authorization", equalTo("Bearer test-access-token"));
        given().get("/api/v1/creatures").then().statusCode(200).body("path", equalTo("/world/api/v1/creatures"));
        given().get("/api/v1/quests").then().statusCode(200).body("path", equalTo("/quest/api/v1/quests"));
        Response session = given().get("/api/v1/session").then().statusCode(200).extract().response();
        given().cookie("hero-association-csrf", session.getCookie("hero-association-csrf"))
            .header("X-CSRF-TOKEN", session.jsonPath().getString("csrfToken")).contentType("application/json").body("{\"commandId\":\"019c4c00-0007-7000-8000-000000000001\"}")
            .post("/api/v1/quests/019c4c00-0003-7000-8000-000000000001/accept").then().statusCode(200)
            .body("path", equalTo("/quest/api/v1/quests/019c4c00-0003-7000-8000-000000000001/accept")).body("method", equalTo("POST"));
    }

}
