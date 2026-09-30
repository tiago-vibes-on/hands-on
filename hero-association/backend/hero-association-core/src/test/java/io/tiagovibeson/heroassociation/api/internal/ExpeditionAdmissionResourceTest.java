package io.tiagovibeson.heroassociation.api.internal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

@QuarkusTest
class ExpeditionAdmissionResourceTest {

    private static final String KEY = "test-only-expedition-core-service-key-0123456789";

    @Test
    void anonymousCallerCannotResolveManager() {
        given().header("X-Hero-Association-Service-Key", KEY)
                .when().get("/internal/v1/expedition-admissions/me")
                .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
    void authenticatedManagerRequiresTheInternalServiceKey() {
        given().header("X-Hero-Association-Service-Key", "invalid")
                .when().get("/internal/v1/expedition-admissions/me")
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
    void resolvesOnlyTheTokenOwnersManager() {
        given().header("X-Hero-Association-Service-Key", KEY)
                .when().get("/internal/v1/expedition-admissions/me")
                .then().statusCode(200)
                .body("managerId", equalTo("019c4c00-0000-7000-8000-000000000001"));
    }
}
