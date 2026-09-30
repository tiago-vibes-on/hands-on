package io.tiagovibeson.heroassociation.api.v1.account;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.tiagovibeson.heroassociation.domain.Account;
import io.tiagovibeson.heroassociation.domain.AgencyMember;
import io.tiagovibeson.heroassociation.domain.AgencyMemberRole;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.repository.AccountRepository;
import io.tiagovibeson.heroassociation.repository.AgencyMemberRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import io.tiagovibeson.heroassociation.repository.ManagerItemRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRuneRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRepository;
import io.tiagovibeson.heroassociation.repository.PartyRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SeededManagerFixtureTest {

    private static final String DAWNWATCH_ID = "019c4c00-0001-7000-8000-000000000001";
    private static final String IRONRIDGE_ID = "019c4c00-0001-7000-8000-000000000002";
    private static final String SILVERKEEP_ID = "019c4c00-0001-7000-8000-000000000003";

    @Inject
    AccountRepository accountRepository;

    @Inject
    ManagerRepository managerRepository;

    @Inject
    PartyRepository partyRepository;

    @Inject
    AgencyMemberRepository agencyMemberRepository;

    @Inject
    HeroRepository heroRepository;

    @Inject
    ManagerItemRepository managerItemRepository;

    @Inject
    ManagerRuneRepository managerRuneRepository;

    @Test
    @Transactional
    void shouldSeedTenManagersAcrossThreeAgencies() {
        for (int number = 1; number <= 10; number++) {
            String subject = "019c4c00-0100-7000-8000-%012d".formatted(100 + number);
            Account account = accountRepository.findByKeycloakSubject(subject).orElseThrow();
            assertEquals("manager%d@mail.com".formatted(number), account.getEmail());
            assertEquals(7, account.getId().version());

            Manager manager = managerRepository.findByAccountId(account.getId()).orElseThrow();
            assertEquals("Manager %d".formatted(number), manager.getDisplayName());
            assertEquals(7, manager.getId().version());
            assertEquals(1, partyRepository.count("ownerManager.id", manager.getId()));
            assertEquals(switch (number) {
                case 2 -> 25;
                case 3 -> 20;
                case 4 -> 200;
                default -> 0;
            }, manager.getGold());
            var personalHeroes = heroRepository.listByManagerId(manager.getId());
            assertEquals(3, personalHeroes.size());
            assertEquals(
                    java.util.Set.of("WARRIOR", "MAGE", "ARCHER"),
                    personalHeroes.stream().map(hero -> hero.getHeroClass().name()).collect(java.util.stream.Collectors.toSet()));
            for (var hero : personalHeroes) {
                assertEquals(manager.getId(), hero.getOwnerManager().getId());
                assertEquals(1, hero.getLevel());
                assertEquals(1, hero.getSkillLevel(HeroSkill.MELEE));
                assertEquals(1, hero.getSkillLevel(HeroSkill.DISTANCE));
                assertEquals(1, hero.getSkillLevel(HeroSkill.MAGIC));
                assertEquals(1, hero.getSkillLevel(HeroSkill.SHIELD));
                assertEquals(false, hero.isRecruitable());
            }
            var personalItems = managerItemRepository.listByManagerId(manager.getId());
            assertEquals(number == 3 || number == 4 ? 1 : 0, personalItems.size());
            if (number == 3) {
                assertEquals("magic-crystal", personalItems.getFirst().getItem().getCode());
                assertEquals(2, personalItems.getFirst().getQuantity());
            } else if (number == 4) {
                assertEquals("iron-ingot", personalItems.getFirst().getItem().getCode());
                assertEquals(5, personalItems.getFirst().getQuantity());
            }
            assertEquals(0, managerRuneRepository.listByManagerId(manager.getId()).size());

            AgencyMember membership = agencyMemberRepository.listByManagerId(manager.getId()).getFirst();
            assertEquals(number <= 4 ? UUID.fromString(DAWNWATCH_ID)
                    : number <= 7 ? UUID.fromString(IRONRIDGE_ID)
                    : UUID.fromString(SILVERKEEP_ID), membership.getAgency().getId());
            assertEquals(number == 8 ? AgencyMemberRole.LEADER : AgencyMemberRole.MANAGER, membership.getRole());
            assertEquals(1, agencyMemberRepository.listByManagerId(manager.getId()).size());
        }

        assertEquals(6, agencyMemberRepository.count("agency.id", UUID.fromString(DAWNWATCH_ID)));
        assertEquals(4, agencyMemberRepository.count("agency.id", UUID.fromString(IRONRIDGE_ID)));
        assertEquals(3, agencyMemberRepository.count("agency.id", UUID.fromString(SILVERKEEP_ID)));
        assertEquals(3, heroRepository.listRecruitable().size());
    }

    @Test
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000002")
    void shouldSeedUser2WithLargePersonalWallet() {
        given()
                .when().get("/api/v1/account")
                .then().statusCode(200)
                .body("manager.gold", is(100000));
        given()
                .when().get("/api/v1/agencies/%s/state".formatted(IRONRIDGE_ID))
                .then().statusCode(200)
                .body("parties.find { it.id == '019c4c00-0002-7000-8000-000000000002' }.name", is("Main Party"))
                .body("parties.find { it.id == '019c4c00-0002-7000-8000-000000000002' }.heroIds.size()", is(3));
    }

    @Test
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
    void shouldNotClaimAnAlreadyOwnedPersonalHero() {
        given()
                .when().post("/api/v1/recruits/019c4c00-0030-7001-8000-000000000001/claim")
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000108")
    void shouldAllowSilverkeepLeaderToReadItsAgency() {
        given()
                .when().get("/api/v1/agencies/%s/state".formatted(SILVERKEEP_ID))
                .then()
                .statusCode(200)
                .body("agency.name", is("Silverkeep Guild"))
                .body("agency.leaderName", is("Manager 8"));
    }

    @Test
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000109")
    void shouldExposeMembershipForSilverkeepManager() {
        given()
                .when().get("/api/v1/account")
                .then()
                .statusCode(200)
                .body("manager.displayName", is("Manager 9"))
                .body("agencyMemberships[0].agencyName", is("Silverkeep Guild"))
                .body("agencyMemberships[0].role", is("MANAGER"));
    }
}
