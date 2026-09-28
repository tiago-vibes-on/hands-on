package io.tiagovibeson.heroassociation.bff.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.Response;

@QuarkusTest
class MarketOrderIdentityResourceTest {

    @Test
    @TestSecurity(user = "market-test-player")
    void shouldReturnOnlyTheAuthenticatedSubjectForTheGateway() {
        Response session = given().when().get("/api/v1/session").then().statusCode(200).extract().response();

        given()
                .cookie("hero-association-csrf", session.getCookie("hero-association-csrf"))
                .header("X-CSRF-TOKEN", session.jsonPath().getString("csrfToken"))
                .when().post("/internal/market-order-identity")
                .then()
                .statusCode(200)
                .header(MarketOrderIdentityResource.SUBJECT_HEADER, equalTo("market-test-player"))
                .header("Cache-Control", equalTo("no-store"));
    }

    @Test
    void shouldRejectAnAnonymousGatewayCheck() {
        Response session = given()
                .when().get("/api/v1/session")
                .then().statusCode(200)
                .extract().response();

        given()
                .cookie("hero-association-csrf", session.getCookie("hero-association-csrf"))
                .header("X-CSRF-TOKEN", session.jsonPath().getString("csrfToken"))
                .when().post("/internal/market-order-identity")
                .then()
                .statusCode(401);
    }
}
