package io.tiagovibeson.heroassociation.api.v1;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CoreAuthenticationTest {

    @Test
    void shouldRejectAnApiRequestWithoutABearerToken() {
        given()
                .when().get("/api/v1/market/orders")
                .then()
                .statusCode(401);
    }
}
