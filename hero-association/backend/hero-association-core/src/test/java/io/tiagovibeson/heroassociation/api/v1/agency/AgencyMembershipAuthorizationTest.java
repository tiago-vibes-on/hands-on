package io.tiagovibeson.heroassociation.api.v1.agency;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "local-seed-soren")
class AgencyMembershipAuthorizationTest {

    private static final String DAWNWATCH_AGENCY_ID = "019c4c00-0001-7000-8000-000000000001";
    private static final String MAGIC_CRYSTAL_ID = "019c4c00-0070-7000-8000-000000000001";

    @Test
    void shouldAllowAManagerToReadTheirAgencyButNotPlaceMarketOrders() {
        given()
                .when().get("/api/v1/agencies/%s/state".formatted(DAWNWATCH_AGENCY_ID))
                .then()
                .statusCode(200)
                .body("agency.name", is("Dawnwatch Agency"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "side": "SELL",
                          "itemId": "%s",
                          "quantity": 1,
                          "priceGoldPerItem": 100
                        }
                        """.formatted(MAGIC_CRYSTAL_ID))
                .when().post("/api/v1/agencies/%s/market-orders".formatted(DAWNWATCH_AGENCY_ID))
                .then()
                .statusCode(403)
                .body("message", is("Only an agency leader can perform this action."));
    }

    @Test
    @TestSecurity(user = "unassigned-manager")
    void shouldRejectAManagerWhoDoesNotBelongToTheAgency() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"displayName\":\"Unassigned Ranger\"}")
                .when().post("/api/v1/account/manager")
                .then()
                .statusCode(200);

        given()
                .when().get("/api/v1/agencies/%s/state".formatted(DAWNWATCH_AGENCY_ID))
                .then()
                .statusCode(403)
                .body("message", is("You are not a member of this agency."));
    }
}
