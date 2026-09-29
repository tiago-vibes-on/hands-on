package io.tiagovibeson.heroassociation.api.v1.agency;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestIdentityAssociation;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "recruiter")
class HeroRecruitmentControllerTest {

    private static final String STEELWARD_RECRUIT_ID = "019c4c00-0010-7000-8000-000000000007";
    private static final String DAWNFLAME_RECRUIT_ID = "019c4c00-0010-7000-8000-000000000008";
    private static final String WINDMARK_RECRUIT_ID = "019c4c00-0010-7000-8000-000000000009";

    @Inject
    TestIdentityAssociation testIdentityAssociation;

    @Test
    void shouldClaimAnAvailableHeroForTheManagerWithoutAgencyMembership() {
        String managerId = given()
                .contentType(ContentType.JSON)
                .body("{\"displayName\":\"Recruiter\"}")
                .when().post("/api/v1/account/manager")
                .then()
                .statusCode(200)
                .extract().path("manager.id");

        given()
                .when().get("/api/v1/recruits")
                .then()
                .statusCode(200)
                .body("alias", hasItems("Steelward", "Dawnflame", "Windmark"))
                .body("heroClass", hasItems("WARRIOR", "MAGE", "ARCHER"));

        given()
                .when().post("/api/v1/recruits/%s/claim".formatted(STEELWARD_RECRUIT_ID))
                .then()
                .statusCode(200)
                .body("id", is(STEELWARD_RECRUIT_ID))
                .body("ownerManagerId", is(managerId))
                .body("name", is("Alden Steelward"))
                .body("alias", is("Steelward"))
                .body("heroClass", is("WARRIOR"))
                .body("level", is(1))
                .body("activity", is("TRAINING"))
                .body("currentHealth", is(300))
                .body("currentMana", is(50))
                .body("stamina", is(100))
                .body("healthRecoveryPerSecond", is(10))
                .body("manaRecoveryPerSecond", is(2))
                .body("runeSlots.size()", is(5));

        given()
                .when().get("/api/v1/account")
                .then()
                .statusCode(200)
                .body("manager.heroes.size()", is(4))
                .body("manager.heroes.id", hasItems(STEELWARD_RECRUIT_ID));

        String agencyId = given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Recruitment Guild\"}")
                .when().post("/api/v1/agencies")
                .then()
                .statusCode(201)
                .extract().path("agency.id");

        given()
                .when().get("/api/v1/agencies/%s/state".formatted(agencyId))
                .then()
                .statusCode(200)
                .body("agency.gold", is(0))
                .body("heroes.size()", is(0))
                .body("personalHeroes.size()", is(4))
                .body("personalHeroes.id", hasItems(STEELWARD_RECRUIT_ID));

        given()
                .when().get("/api/v1/agencies/%s/heroes/%s".formatted(agencyId, STEELWARD_RECRUIT_ID))
                .then()
                .statusCode(404);

        given()
                .when().get("/api/v1/recruits")
                .then()
                .statusCode(200)
                .body("size()", is(2));

        given()
                .when().post("/api/v1/recruits/%s/claim".formatted(STEELWARD_RECRUIT_ID))
                .then()
                .statusCode(409)
                .body("message", is("Recruit with id %s is no longer available.".formatted(STEELWARD_RECRUIT_ID)));

        given()
                .when().post("/api/v1/agencies/%s/recruits/%s/claim".formatted(agencyId, DAWNFLAME_RECRUIT_ID))
                .then()
                .statusCode(200)
                .body("heroes.size()", is(1))
                .body("heroes[0].id", is(DAWNFLAME_RECRUIT_ID))
                .body("heroes[0].ownerManagerId", nullValue())
                .body("personalHeroes.size()", is(4));

        given()
                .when().get("/api/v1/agencies/%s/heroes/%s".formatted(agencyId, DAWNFLAME_RECRUIT_ID))
                .then()
                .statusCode(200)
                .body("id", is(DAWNFLAME_RECRUIT_ID));

        given()
                .when().get("/api/v1/recruits")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("alias", hasItems("Windmark"));

        given()
                .when().post("/api/v1/agencies/%s/recruits/%s/claim".formatted(agencyId, DAWNFLAME_RECRUIT_ID))
                .then().statusCode(409);

        given()
                .when().post("/api/v1/recruits/%s/claim".formatted(DAWNFLAME_RECRUIT_ID))
                .then().statusCode(409);

        SecurityIdentity recruiterIdentity = testIdentityAssociation.getTestIdentity();
        try {
            testIdentityAssociation.setTestIdentity(QuarkusSecurityIdentity.builder(recruiterIdentity)
                    .setPrincipal(() -> "other-recruiter")
                    .build());

            given()
                    .contentType(ContentType.JSON)
                    .body("{\"displayName\":\"Other Recruiter\"}")
                    .when().post("/api/v1/account/manager")
                    .then()
                    .statusCode(200)
                    .body("manager.heroes.size()", is(3));

            given()
                    .when().post("/api/v1/recruits/%s/claim".formatted(STEELWARD_RECRUIT_ID))
                    .then()
                    .statusCode(409);
        } finally {
            testIdentityAssociation.setTestIdentity(recruiterIdentity);
        }
    }

    @Test
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000109")
    void shouldRejectAgencyRecruitmentByNonLeader() {
        given()
                .when().post("/api/v1/agencies/019c4c00-0001-7000-8000-000000000003/recruits/%s/claim"
                        .formatted(WINDMARK_RECRUIT_ID))
                .then()
                .statusCode(403)
                .body("message", is("Only an agency leader can perform this action."));
    }
}
