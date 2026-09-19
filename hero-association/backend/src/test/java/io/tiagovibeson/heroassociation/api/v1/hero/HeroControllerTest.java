package io.tiagovibeson.heroassociation.api.v1.hero;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HeroControllerTest {

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
                .when().post("/api/v1/heroes")
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
                .when().put("/api/v1/heroes/{id}", id)
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
                .when().patch("/api/v1/heroes/{id}", id)
                .then()
                .statusCode(200)
                .body("name", is("Diana Prince"))
                .body("alias", is("Themysciran Champion"))
                .body("power", is("Flight"));

        given()
                .when().get("/api/v1/heroes/{id}", id)
                .then()
                .statusCode(200)
                .body("alias", is("Themysciran Champion"));

        given()
                .when().delete("/api/v1/heroes/{id}", id)
                .then()
                .statusCode(204);

        given()
                .when().get("/api/v1/heroes/{id}", id)
                .then()
                .statusCode(404);
    }

    @Test
    void shouldRejectAnAliasAlreadyUsedByAnotherHero() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Barry Allen",
                          "alias": "The Flash",
                          "power": "Speed"
                        }
                        """)
                .when().post("/api/v1/heroes")
                .then()
                .statusCode(201);

        Number secondHeroId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Hal Jordan",
                          "alias": "Green Lantern",
                          "power": "Willpower"
                        }
                        """)
                .when().post("/api/v1/heroes")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Hal Jordan",
                          "alias": "The Flash",
                          "power": "Willpower"
                        }
                        """)
                .when().put("/api/v1/heroes/{id}", secondHeroId.longValue())
                .then()
                .statusCode(409)
                .body("message", is("Alias 'The Flash' is already registered."));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "alias": "The Flash"
                        }
                        """)
                .when().patch("/api/v1/heroes/{id}", secondHeroId.longValue())
                .then()
                .statusCode(409)
                .body("message", is("Alias 'The Flash' is already registered."));
    }

    @Test
    void shouldExposeOnlyTheVersionedApi() {
        given()
                .when().get("/heroes")
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
                .when().post("/api/v1/heroes")
                .then()
                .statusCode(400);
    }

    @Test
    void shouldRejectAnEmptyHeroUpdate() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().patch("/api/v1/heroes/1")
                .then()
                .statusCode(400)
                .body("message", is("At least one field must be provided."));
    }

    @Test
    void shouldReturnNotFoundForAnUnknownHero() {
        given()
                .when().get("/api/v1/heroes/999999")
                .then()
                .statusCode(404)
                .body("message", is("Hero with id 999999 was not found."));
    }
}
