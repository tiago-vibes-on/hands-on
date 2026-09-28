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
import io.tiagovibeson.heroassociation.repository.AccountRepository;
import io.tiagovibeson.heroassociation.repository.AgencyMemberRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRepository;
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
    AgencyMemberRepository agencyMemberRepository;

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
