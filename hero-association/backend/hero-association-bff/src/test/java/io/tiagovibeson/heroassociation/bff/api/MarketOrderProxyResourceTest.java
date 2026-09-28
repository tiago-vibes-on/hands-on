package io.tiagovibeson.heroassociation.bff.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.Response;
import io.tiagovibeson.heroassociation.bff.testsupport.GameCoreStubResource;

@QuarkusTest
@QuarkusTestResource(GameCoreStubResource.class)
@TestSecurity(user = "market-test-player")
class MarketOrderProxyResourceTest {

    @Test
    void shouldProxyPlacementAcrossAgenciesWithoutLimitingReadsOrCancellation() {
        Response session = given()
                .when().get("/api/v1/session")
                .then().statusCode(200)
                .extract().response();
        String csrfToken = session.jsonPath().getString("csrfToken");
        String csrfCookie = session.getCookie("hero-association-csrf");
        String path = "/api/v1/market/orders";

        for (int attempt = 0; attempt < 6; attempt++) {
            given()
                    .cookie("hero-association-csrf", csrfCookie)
                    .header("X-CSRF-TOKEN", csrfToken)
                    .contentType("application/json")
                    .body("{\"agencyId\":\"agency-a\",\"side\":\"BUY\"}")
                    .when().post(path)
                    .then().statusCode(200)
                    .body("method", equalTo("POST"));
        }

        given()
                .cookie("hero-association-csrf", csrfCookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType("application/json")
                .body("{\"agencyId\":\"agency-b\",\"side\":\"SELL\"}")
                .when().post(path)
                .then().statusCode(200)
                .body("method", equalTo("POST"));

        given().when().get(path).then().statusCode(200);
        given()
                .cookie("hero-association-csrf", csrfCookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .when().delete(path + "/order-a")
                .then().statusCode(200);
    }
}
