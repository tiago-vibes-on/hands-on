package io.tiagovibeson.heroassociation.api.v1.agency;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HeroBorrowingControllerTest {

    private static final String AGENCY_ID = "019c4c00-0001-7000-8000-000000000001";
    private static final String IRONWALL_ID = "019c4c00-0010-7000-8000-000000000001";
    private static final String PERSONAL_HERO_ID = "019c4c00-0030-7001-8000-000000000001";

    @Test
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
    void leaderCanSetAndResetAgencyHeroFee() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"feeGold\":15}")
                .when().put("/api/v1/agencies/%s/heroes/%s/borrowing-fee".formatted(AGENCY_ID, IRONWALL_ID))
                .then().statusCode(200)
                .body("heroes.find { it.id == '%s' }.borrowingFeeGold".formatted(IRONWALL_ID), is(15));

        given()
                .contentType(ContentType.JSON)
                .body("{\"feeGold\":-1}")
                .when().put("/api/v1/agencies/%s/heroes/%s/borrowing-fee".formatted(AGENCY_ID, IRONWALL_ID))
                .then().statusCode(400);

        given()
                .contentType(ContentType.JSON)
                .body("{\"feeGold\":0}")
                .when().put("/api/v1/agencies/%s/heroes/%s/borrowing-fee".formatted(AGENCY_ID, PERSONAL_HERO_ID))
                .then().statusCode(404);

        given()
                .contentType(ContentType.JSON)
                .body("{\"feeGold\":0}")
                .when().put("/api/v1/agencies/%s/heroes/%s/borrowing-fee".formatted(AGENCY_ID, IRONWALL_ID))
                .then().statusCode(200)
                .body("heroes.find { it.id == '%s' }.borrowingFeeGold".formatted(IRONWALL_ID), is(0));
    }

    @Test
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000101")
    void memberCannotChangeAgencyHeroFee() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"feeGold\":25}")
                .when().put("/api/v1/agencies/%s/heroes/%s/borrowing-fee".formatted(AGENCY_ID, IRONWALL_ID))
                .then().statusCode(403)
                .body("message", is("Only an agency leader can perform this action."));
    }
}
