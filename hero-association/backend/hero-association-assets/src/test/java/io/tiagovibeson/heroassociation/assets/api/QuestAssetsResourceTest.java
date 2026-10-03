package io.tiagovibeson.heroassociation.assets.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import java.util.Map;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import org.junit.jupiter.api.Test;

@QuarkusTest
class QuestAssetsResourceTest {
    @Test void questAndCoreCredentialsCannotUseEachOthersRewardRoutes() {
        var body = Map.of("operationKey", UuidV7.next(), "kind", "QUEST_REWARD", "managerId", UuidV7.next(), "gold", 0, "items", Map.of(), "runes", Map.of());
        given().contentType("application/json").body(body).post("/internal/v1/assets/quest/rewards").then().statusCode(403);
        given().header("X-Hero-Association-Assets-Quest-Service-Key", "test-only-assets-core-service-key-0123456789").contentType("application/json").body(body).post("/internal/v1/assets/quest/rewards").then().statusCode(403);
        given().header("X-Hero-Association-Assets-Core-Service-Key", "test-only-assets-core-service-key-0123456789").contentType("application/json").body(body).post("/internal/v1/assets/core/commands").then().statusCode(403);
        given().header("X-Hero-Association-Assets-Quest-Service-Key", "test-only-assets-quest-service-key-0123456789").contentType("application/json").body(body).post("/internal/v1/assets/quest/rewards").then().statusCode(200).body("status", is("APPLIED"));
        given().header("X-Hero-Association-Assets-Quest-Service-Key", "test-only-assets-quest-service-key-0123456789").contentType("application/json").body(body).post("/internal/v1/assets/quest/rewards").then().statusCode(200).body("status", is("APPLIED"));
    }
}
