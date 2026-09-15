package io.tiagovibes.heroassociation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class StatusResourceTest {

    @Test
    void shouldReportThatTheServiceIsRunning() {
        given()
                .when().get("/status")
                .then()
                .statusCode(200)
                .body("service", is("hero-association"))
                .body("status", is("UP"));
    }
}
