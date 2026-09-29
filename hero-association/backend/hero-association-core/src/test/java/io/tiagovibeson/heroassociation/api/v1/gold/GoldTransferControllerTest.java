package io.tiagovibeson.heroassociation.api.v1.gold;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "019c4c00-0100-7000-8000-000000000104")
class GoldTransferControllerTest {

    @Test
    void rejectsZeroGold() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"direction":"MANAGER_TO_AGENCY","agencyName":"Dawnwatch Agency","amountGold":0}
                        """)
                .when().post("/api/v1/gold-transfers")
                .then().statusCode(400)
                .body("message", is("A transfer direction and a positive whole gold amount are required."));
    }

    @Test
    void rejectsFractionalGoldRatherThanRoundingIt() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"direction":"MANAGER_TO_AGENCY","agencyName":"Dawnwatch Agency","amountGold":1.5}
                        """)
                .when().post("/api/v1/gold-transfers")
                .then().statusCode(400)
                .body("message", is("Gold amount must be a positive whole number within the supported range."));
    }

    @Test
    void rejectsUnknownAgency() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"direction":"MANAGER_TO_AGENCY","agencyName":"No Such Agency","amountGold":1}
                        """)
                .when().post("/api/v1/gold-transfers")
                .then().statusCode(404)
                .body("message", is("The requested agency was not found."));
    }

    @Test
    void forbidsNonLeaderWithdrawal() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"direction":"AGENCY_TO_MANAGER","agencyName":"Dawnwatch Agency","managerName":"Manager 4","amountGold":1}
                        """)
                .when().post("/api/v1/gold-transfers")
                .then().statusCode(403);
    }

    @Test
    void rejectsInsufficientPersonalGold() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"direction":"MANAGER_TO_AGENCY","agencyName":"Silverkeep Guild","amountGold":1000000}
                        """)
                .when().post("/api/v1/gold-transfers")
                .then().statusCode(409)
                .body("message", is("The Manager does not have enough gold."));
    }
}
