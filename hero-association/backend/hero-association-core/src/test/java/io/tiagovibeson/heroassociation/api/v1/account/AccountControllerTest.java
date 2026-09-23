package io.tiagovibeson.heroassociation.api.v1.account;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "onboarding-player")
class AccountControllerTest {

    @Test
    void shouldProvisionAnAccountAndCreateItsManager() {
        String accountId = given()
                .when().get("/api/v1/account")
                .then()
                .statusCode(200)
                .body("keycloakSubject", is("onboarding-player"))
                .body("status", is("ACTIVE"))
                .body("manager", is(org.hamcrest.Matchers.nullValue()))
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("{\"displayName\":\"Wayfinder\"}")
                .when().post("/api/v1/account/manager")
                .then()
                .statusCode(200)
                .body("id", is(accountId))
                .body("manager.id", notNullValue())
                .body("manager.displayName", is("Wayfinder"));

        given()
                .when().get("/api/v1/account")
                .then()
                .statusCode(200)
                .body("id", is(accountId))
                .body("manager.displayName", is("Wayfinder"));
    }

    @Test
    @TestSecurity(user = "repeat-manager-player")
    void shouldRejectCreatingAnotherManagerForTheSameAccount() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"displayName\":\"Repeat Manager\"}")
                .when().post("/api/v1/account/manager")
                .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("{\"displayName\":\"Another Wayfinder\"}")
                .when().post("/api/v1/account/manager")
                .then()
                .statusCode(409)
                .body("message", is("This account has already completed manager onboarding."));
    }

    @Test
    @TestSecurity(user = "different-onboarding-player")
    void shouldRejectAnExistingManagerDisplayNameWithoutCaseSensitivity() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"displayName\":\"user 1\"}")
                .when().post("/api/v1/account/manager")
                .then()
                .statusCode(409)
                .body("message", is("The manager display name user 1 is already in use."));
    }

    @Test
    @TestSecurity(user = "invalid-manager-name-player")
    void shouldRejectAnInvalidManagerDisplayName() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"displayName\":\"  \"}")
                .when().post("/api/v1/account/manager")
                .then()
                .statusCode(400)
                .body("message", is("A manager display name must contain between 3 and 100 characters."));
    }
}
