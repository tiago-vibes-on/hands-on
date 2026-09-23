package io.tiagovibeson.heroassociation.bff.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SessionResourceTest {

    @Test
    void shouldDescribeAnAnonymousSessionAndIssueACsrfCookie() {
        given()
                .when().get("/api/v1/session")
                .then()
                .statusCode(200)
                .header("Cache-Control", "no-store")
                .cookie("hero-association-csrf")
                .body("authenticated", is(false))
                .body("identity", nullValue())
                .body("csrfToken", notNullValue());
    }
}
