package io.tiagovibeson.heroassociation.bff.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest @TestSecurity(user="assets-player")
class AssetsProxyResourceTest {
    @Test void goldTransferTargetsAssetsAndEquipmentKeepsItsCoreOperationKey() {
        var session=given().get("/api/v1/session").then().statusCode(200).extract().response();
        String csrf=session.jsonPath().getString("csrfToken"),cookie=session.getCookie("hero-association-csrf");
        given().cookie("hero-association-csrf",cookie).header("X-CSRF-TOKEN",csrf).contentType("application/json").body("{\"operationKey\":\"accepted-key\"}")
            .post("/api/v1/gold-transfers").then().statusCode(200).body("path",equalTo("/assets/api/v1/gold-transfers")).body("authorization",equalTo("Bearer test-access-token"));
        given().cookie("hero-association-csrf",cookie).header("X-CSRF-TOKEN",csrf).header("X-Operation-Key","original-key")
            .delete("/api/v1/agencies/agency/heroes/hero/rune-slots/0").then().statusCode(200).body("operationKey",equalTo("original-key")).body("path",equalTo("/api/v1/agencies/agency/heroes/hero/rune-slots/0"));
    }
}
