package io.tiagovibeson.heroassociation.bff.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

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
                .body("authorization", equalTo("Bearer test-access-token"));
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
}
