package io.tiagovibeson.heroassociation.api.internal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.tiagovibeson.heroassociation.testsupport.AssetsStubResource;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(AssetsStubResource.class)
class AssetsResourceTest {
    private static final String BASE = "/internal/v1/asset-authority";
    private static final String HEADER = "X-Hero-Association-Assets-Core-Service-Key";
    private static final String MANAGER = "019c4c00-0000-7000-8000-000000000204";
    private static final String AGENCY = "019c4c00-0001-7000-8000-000000000001";
    @Test void serviceCredentialAloneCannotResolvePlayerAuthority() {
        given().header(HEADER, AssetsStubResource.KEY).contentType("application/json").body("{\"ownerType\":\"MANAGER\"}")
            .post(BASE + "/context").then().statusCode(401);
    }
    @Test @TestSecurity(user = "019c4c00-0100-7000-8000-000000000104")
    void tokenAloneAndAnotherServicesCredentialFailClosed() {
        for (String key : new String[] { "", "test-only-assets-market-service-key-0123456789", "test-only-expedition-core-service-key-0123456789" })
            given().header(HEADER, key).contentType("application/json").body("{\"ownerType\":\"MANAGER\"}")
                .post(BASE + "/context").then().statusCode(403);
    }
    @Test @TestSecurity(user = "019c4c00-0100-7000-8000-000000000104")
    void resolvesCurrentManagerAndRejectsOtherManagersAndMemberWithdrawals() {
        context("{\"ownerType\":\"MANAGER\"}").then().statusCode(200).body("managerId", equalTo(MANAGER)).body("ownerId", equalTo(MANAGER));
        context("{\"ownerType\":\"MANAGER\",\"ownerId\":\"019c4c00-0000-7000-8000-000000000001\"}").then().statusCode(403);
        context("{\"ownerType\":\"AGENCY\",\"ownerId\":\"" + AGENCY + "\"}").then().statusCode(403);
        transfer("AGENCY_TO_MANAGER", "Dawnwatch Agency", "User 1").then().statusCode(403);
        transfer("MANAGER_TO_AGENCY", "Silverkeep Guild", null).then().statusCode(200).body("managerId", equalTo(MANAGER));
    }
    @Test @TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
    void leaderCanAuthorizeAgencyAssetsAndNamedRecipient() {
        context("{\"ownerType\":\"AGENCY\",\"ownerId\":\"" + AGENCY + "\"}").then().statusCode(200).body("ownerId", equalTo(AGENCY));
        transfer("AGENCY_TO_MANAGER", " dawnwatch agency ", " manager 4 ").then().statusCode(200).body("managerId", equalTo(MANAGER));
    }
    @Test @TestSecurity(user = "019c4c00-0100-7000-8000-000000000002")
    void leadershipOfAnotherAgencyDoesNotGrantAuthority() { context("{\"ownerType\":\"AGENCY\",\"ownerId\":\"" + AGENCY + "\"}").then().statusCode(403); }
    @Test @TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
    void malformedAuthorityRequestsFailBeforeResolvingAnOwner() { context("{}").then().statusCode(400); context("null").then().statusCode(400); }
    @Test @TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
    void retiredEconomicEndpointsHaveNoCoreWriter() {
        given().get("/internal/v1/assets/reservations/019c4c00-0092-7000-8000-000000000001").then().statusCode(404);
        given().contentType("application/json").body("{}").post("/api/v1/gold-transfers").then().statusCode(404);
    }
    private io.restassured.response.Response context(String body) { return given().header(HEADER, AssetsStubResource.KEY).contentType("application/json").body(body).post(BASE + "/context"); }
    private io.restassured.response.Response transfer(String direction, String agency, String manager) {
        var body = new java.util.HashMap<String, Object>(); body.put("direction", direction); body.put("agencyName", agency); body.put("managerName", manager);
        return given().header(HEADER, AssetsStubResource.KEY).contentType("application/json").body(body).post(BASE + "/transfers");
    }
}
