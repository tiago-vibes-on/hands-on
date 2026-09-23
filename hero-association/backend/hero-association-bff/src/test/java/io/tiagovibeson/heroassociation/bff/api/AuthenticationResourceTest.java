package io.tiagovibeson.heroassociation.bff.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AuthenticationResourceTest {

    @Test
    void shouldRedirectToTheFrontendAfterAValidatedProviderLogout() {
        given()
                .redirects().follow(false)
                .cookie("q_post_logout", "expected-state")
                .queryParam("state", "expected-state")
                .when().get("/auth/post-logout")
                .then()
                .statusCode(303)
                .header("Location", is("http://localhost:5173"));
    }

    @Test
    void shouldRejectAnUnvalidatedProviderLogoutCallback() {
        given()
                .redirects().follow(false)
                .queryParam("state", "unexpected-state")
                .when().get("/auth/post-logout")
                .then()
                .statusCode(400);
    }
}
