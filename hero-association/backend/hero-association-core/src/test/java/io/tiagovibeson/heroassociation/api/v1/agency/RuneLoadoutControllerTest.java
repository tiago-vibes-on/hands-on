package io.tiagovibeson.heroassociation.api.v1.agency;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.tiagovibeson.heroassociation.domain.RuneInventoryOwnerType;
import io.tiagovibeson.heroassociation.repository.AgencyRuneRepository;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
class RuneLoadoutControllerTest {

    private static final String AGENCY_ID = "019c4c00-0001-7000-8000-000000000001";
    private static final String USER2_AGENCY_ID = "019c4c00-0001-7000-8000-000000000002";
    private static final String IRONWALL_ID = "019c4c00-0010-7000-8000-000000000001";
    private static final String EMBERVEIL_ID = "019c4c00-0010-7000-8000-000000000005";
    private static final String USER2_WARRIOR_ID = "019c4c00-0030-7001-8000-000000000002";
    private static final String ATTACK_RUNE_ID = "019c4c00-0020-7000-8000-000000000001";
    private static final String VITALITY_RUNE_ID = "019c4c00-0020-7000-8000-000000000003";
    private static final String CRITICAL_CHANCE_RUNE_ID = "019c4c00-0020-7000-8000-000000000006";
    private static final String GUARD_RUNE_ID = "019c4c00-0020-7000-8000-000000000002";

    @Inject
    AgencyRuneRepository agencyRuneRepository;


    @Test
    void shouldMoveRunesAtomicallyBetweenInventoryAndHeroSlots() {
        equip(EMBERVEIL_ID, 0, ATTACK_RUNE_ID)
                .then()
                .statusCode(200)
                .body("heroes.find { it.alias == 'Emberveil' }.runeSlots[0].rune.code", is("attack-rune"))
                .body("runeInventory.find { it.rune.code == 'attack-rune' }.quantity", is(0));

        unequip(EMBERVEIL_ID, 0)
                .then()
                .statusCode(200)
                .body("heroes.find { it.alias == 'Emberveil' }.runeSlots[0].rune", nullValue())
                .body("runeInventory.find { it.rune.code == 'attack-rune' }.quantity", is(0))
                .body("personalRuneInventory.find { it.rune.code == 'attack-rune' }.quantity", is(1));

        equip(EMBERVEIL_ID, 0, VITALITY_RUNE_ID)
                .then()
                .statusCode(200)
                .body("heroes.find { it.alias == 'Emberveil' }.runeSlots[0].rune.code", is("vitality-rune"))
                .body("personalRuneInventory.find { it.rune.code == 'attack-rune' }.quantity", is(1))
                .body("runeInventory.find { it.rune.code == 'vitality-rune' }.quantity", is(0));

        equip(EMBERVEIL_ID, 0, ATTACK_RUNE_ID, RuneInventoryOwnerType.MANAGER)
                .then()
                .statusCode(200)
                .body("heroes.find { it.alias == 'Emberveil' }.runeSlots[0].rune.code", is("attack-rune"))
                .body("runeInventory.find { it.rune.code == 'attack-rune' }.quantity", is(0))
                .body("personalRuneInventory.find { it.rune.code == 'attack-rune' }.quantity", is(0))
                .body("personalRuneInventory.find { it.rune.code == 'vitality-rune' }.quantity", is(1));
    }

    @Test
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000002")
    void shouldEquipPersonalHeroFromManagerInventory() {
        unequip(USER2_AGENCY_ID, USER2_WARRIOR_ID, 0)
                .then()
                .statusCode(200)
                .body("personalHeroes.find { it.id == '%s' }.runeSlots[0].rune".formatted(USER2_WARRIOR_ID), nullValue())
                .body("personalRuneInventory.find { it.rune.code == 'attack-rune' }.quantity", is(1));

        equip(USER2_AGENCY_ID, USER2_WARRIOR_ID, 0, ATTACK_RUNE_ID, RuneInventoryOwnerType.MANAGER)
                .then()
                .statusCode(200)
                .body("personalHeroes.find { it.id == '%s' }.runeSlots[0].rune.code".formatted(USER2_WARRIOR_ID), is("attack-rune"))
                .body("personalRuneInventory.find { it.rune.code == 'attack-rune' }.quantity", is(0));
    }

    @Test
    void shouldNotAllowAnotherManagersPersonalHeroToBeChanged() {
        unequip(USER2_WARRIOR_ID, 0).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "local-seed-soren")
    void shouldAllowAgencyMembersToManageAgencyHeroRunes() {
        QuarkusTransaction.requiringNew().run(() ->
                agencyRuneRepository.find("agency.id = ?1 and rune.id = ?2",
                        java.util.UUID.fromString(AGENCY_ID), java.util.UUID.fromString(GUARD_RUNE_ID))
                        .firstResult().increaseQuantity());

        equip(EMBERVEIL_ID, 4, GUARD_RUNE_ID)
                .then()
                .statusCode(200)
                .body("heroes.find { it.alias == 'Emberveil' }.runeSlots[4].rune.code", is("guard-rune"))
                .body("runeInventory.find { it.rune.code == 'guard-rune' }.quantity", is(0));

        unequip(EMBERVEIL_ID, 4)
                .then()
                .statusCode(200)
                .body("heroes.find { it.alias == 'Emberveil' }.runeSlots[4].rune", nullValue())
                .body("personalRuneInventory.find { it.rune.code == 'guard-rune' }.quantity", is(1));
    }

    @Test
    void shouldRejectLoadoutChangesForAHeroOnAQuest() {
        String message = "Hero with id %s is on a quest and is not available for this action.".formatted(IRONWALL_ID);

        equip(IRONWALL_ID, 0, VITALITY_RUNE_ID)
                .then()
                .statusCode(409)
                .body("message", is(message));

        unequip(IRONWALL_ID, 0)
                .then()
                .statusCode(409)
                .body("message", is(message));
    }

    @Test
    void shouldRejectARuneThatIsNotAvailableInTheAgencyInventory() {
        equip(EMBERVEIL_ID, 0, CRITICAL_CHANCE_RUNE_ID)
                .then()
                .statusCode(409)
                .body("message", is("Rune with id %s is not available in this agency inventory.".formatted(CRITICAL_CHANCE_RUNE_ID)));
    }

    @Test
    void shouldRejectAnInvalidRuneSlot() {
        equip(EMBERVEIL_ID, 5, ATTACK_RUNE_ID)
                .then()
                .statusCode(400)
                .body("message", is("Rune slot 5 is invalid. A hero has slots 0 through 4."));
    }

    private io.restassured.response.Response equip(String heroId, int slotIndex, String runeId) {
        return equip(heroId, slotIndex, runeId, RuneInventoryOwnerType.AGENCY);
    }

    private io.restassured.response.Response equip(String heroId, int slotIndex, String runeId,
            RuneInventoryOwnerType sourceOwnerType) {
        return equip(AGENCY_ID, heroId, slotIndex, runeId, sourceOwnerType);
    }

    private io.restassured.response.Response equip(String agencyId, String heroId, int slotIndex, String runeId,
            RuneInventoryOwnerType sourceOwnerType) {
        return given()
                .contentType(ContentType.JSON)
                .body(new EquipRuneRequest(java.util.UUID.fromString(runeId), sourceOwnerType))
                .when()
                .put(slotPath(agencyId, heroId, slotIndex));
    }

    private io.restassured.response.Response unequip(String heroId, int slotIndex) {
        return unequip(AGENCY_ID, heroId, slotIndex);
    }

    private io.restassured.response.Response unequip(String agencyId, String heroId, int slotIndex) {
        return given()
                .when()
                .delete(slotPath(agencyId, heroId, slotIndex));
    }

    private String slotPath(String agencyId, String heroId, int slotIndex) {
        return "/api/v1/agencies/%s/heroes/%s/rune-slots/%d".formatted(agencyId, heroId, slotIndex);
    }
}
