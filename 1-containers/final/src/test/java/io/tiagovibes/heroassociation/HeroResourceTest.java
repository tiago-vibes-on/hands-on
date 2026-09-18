package io.tiagovibes.heroassociation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HeroResourceTest {

    @Test
    void shouldCreateReplaceAndPatchAHero() {
        Number createdId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Diana Prince",
                          "alias": "Wonder Woman",
                          "power": "Superhuman strength"
                        }
                        """)
                .when().post("/heroes")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("alias", is("Wonder Woman"))
                .extract().path("id");

        Long id = createdId.longValue();

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Diana Prince",
                          "alias": "Wonder Woman",
                          "power": "Flight"
                        }
                        """)
                .when().put("/heroes/{id}", id)
                .then()
                .statusCode(200)
                .body("power", is("Flight"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "alias": "Themysciran Champion"
                        }
                        """)
                .when().patch("/heroes/{id}", id)
                .then()
                .statusCode(200)
                .body("name", is("Diana Prince"))
                .body("alias", is("Themysciran Champion"))
                .body("power", is("Flight"));

        given()
                .when().get("/heroes/{id}", id)
                .then()
                .statusCode(200)
                .body("alias", is("Themysciran Champion"));

        given()
                .when().delete("/heroes/{id}", id)
                .then()
                .statusCode(204);

        given()
                .when().get("/heroes/{id}", id)
                .then()
                .statusCode(404);
    }

    @Test
    void shouldRejectAnInvalidHero() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "",
                          "alias": "",
                          "power": ""
                        }
                        """)
                .when().post("/heroes")
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturnNotFoundForAnUnknownHero() {
        given()
                .when().get("/heroes/999999")
                .then()
                .statusCode(404)
                .body("message", is("Hero with id 999999 was not found."));
    }
}
