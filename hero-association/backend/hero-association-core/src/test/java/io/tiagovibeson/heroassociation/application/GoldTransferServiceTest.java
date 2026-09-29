package io.tiagovibeson.heroassociation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.tiagovibeson.heroassociation.api.v1.gold.GoldTransferRequest;
import io.tiagovibeson.heroassociation.api.v1.gold.GoldTransferResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyMembershipRequiredException;
import io.tiagovibeson.heroassociation.application.exception.AgencyLeaderRequiredException;
import io.tiagovibeson.heroassociation.application.exception.GoldTransferRejectedException;
import io.tiagovibeson.heroassociation.domain.GoldTransferDirection;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRepository;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "019c4c00-0100-7000-8000-000000000001")
class GoldTransferServiceTest {

    private static final UUID DAWNWATCH_ID = UUID.fromString("019c4c00-0001-7000-8000-000000000001");
    private static final UUID SILVERKEEP_ID = UUID.fromString("019c4c00-0001-7000-8000-000000000003");
    private static final UUID USER1_ID = UUID.fromString("019c4c00-0000-7000-8000-000000000001");
    private static final UUID MANAGER4_ID = UUID.fromString("019c4c00-0000-7000-8000-000000000204");
    private static final UUID MANAGER10_ID = UUID.fromString("019c4c00-0000-7000-8000-000000000210");

    @Inject
    GoldTransferService transferService;

    @Inject
    ManagerRepository managerRepository;

    @Inject
    AgencyRepository agencyRepository;

    @Test
    @TestTransaction
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000104")
    void anyManagerCanSendGoldToAnotherAgencyWithoutAnAgencyShareOrFee() {
        long managerGold = managerRepository.findById(MANAGER4_ID).getGold();
        long agencyGold = agencyRepository.findById(SILVERKEEP_ID).getGold();

        GoldTransferResponse response = transferService.transfer(new GoldTransferRequest(
                GoldTransferDirection.MANAGER_TO_AGENCY, "  silverkeep GUILD  ", null, 30));

        assertEquals(MANAGER4_ID, response.managerId());
        assertEquals(SILVERKEEP_ID, response.agencyId());
        assertEquals(managerGold - 30, response.managerGold());
        assertEquals(agencyGold + 30, response.agencyGold());
        assertEquals(managerGold + agencyGold, response.managerGold() + response.agencyGold());
    }

    @Test
    @TestTransaction
    void leaderCanSendAgencyGoldToAnyManagerOutsideTheAgency() {
        long managerGold = managerRepository.findById(MANAGER10_ID).getGold();
        long agencyGold = agencyRepository.findById(DAWNWATCH_ID).getGold();

        GoldTransferResponse response = transferService.transfer(new GoldTransferRequest(
                GoldTransferDirection.AGENCY_TO_MANAGER, "Dawnwatch Agency", "manager 10", 40));

        assertEquals(MANAGER10_ID, response.managerId());
        assertEquals(managerGold + 40, response.managerGold());
        assertEquals(agencyGold - 40, response.agencyGold());
        assertEquals(managerGold + agencyGold, response.managerGold() + response.agencyGold());
    }

    @Test
    @TestTransaction
    void leaderCanSendAgencyGoldToSelf() {
        long managerGold = managerRepository.findById(USER1_ID).getGold();

        GoldTransferResponse response = transferService.transfer(new GoldTransferRequest(
                GoldTransferDirection.AGENCY_TO_MANAGER, "Dawnwatch Agency", "User 1", 10));

        assertEquals(USER1_ID, response.managerId());
        assertEquals(managerGold + 10, response.managerGold());
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000104")
    void nonLeaderCannotSendAgencyGoldEvenToSelf() {
        long managerGold = managerRepository.findById(MANAGER4_ID).getGold();
        long agencyGold = agencyRepository.findById(DAWNWATCH_ID).getGold();

        assertThrows(AgencyLeaderRequiredException.class, () -> transferService.transfer(
                new GoldTransferRequest(GoldTransferDirection.AGENCY_TO_MANAGER,
                        "Dawnwatch Agency", "Manager 4", 10)));

        assertEquals(managerGold, managerRepository.findById(MANAGER4_ID).getGold());
        assertEquals(agencyGold, agencyRepository.findById(DAWNWATCH_ID).getGold());
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000002")
    void leaderOfAnotherAgencyCannotWithdrawFromThisOne() {
        assertThrows(AgencyMembershipRequiredException.class, () -> transferService.transfer(
                new GoldTransferRequest(GoldTransferDirection.AGENCY_TO_MANAGER,
                        "Dawnwatch Agency", "User 2", 1)));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000104")
    void nonLeaderCannotProbeWhetherRecipientExists() {
        assertThrows(AgencyLeaderRequiredException.class, () -> transferService.transfer(
                new GoldTransferRequest(GoldTransferDirection.AGENCY_TO_MANAGER,
                        "Dawnwatch Agency", "Unknown Manager", 1)));
    }

    @Test
    @TestTransaction
    void insufficientAgencyGoldDoesNotMoveAnyGold() {
        long managerGold = managerRepository.findById(MANAGER10_ID).getGold();
        long agencyGold = agencyRepository.findById(DAWNWATCH_ID).getGold();

        GoldTransferRejectedException error = assertThrows(GoldTransferRejectedException.class,
                () -> transferService.transfer(new GoldTransferRequest(
                        GoldTransferDirection.AGENCY_TO_MANAGER, "Dawnwatch Agency",
                        "Manager 10", agencyGold + 1)));

        assertEquals(409, error.status());
        assertEquals(managerGold, managerRepository.findById(MANAGER10_ID).getGold());
        assertEquals(agencyGold, agencyRepository.findById(DAWNWATCH_ID).getGold());
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "019c4c00-0100-7000-8000-000000000104")
    void insufficientPersonalGoldDoesNotMoveAnyGold() {
        long managerGold = managerRepository.findById(MANAGER4_ID).getGold();
        long agencyGold = agencyRepository.findById(SILVERKEEP_ID).getGold();

        GoldTransferRejectedException error = assertThrows(GoldTransferRejectedException.class,
                () -> transferService.transfer(new GoldTransferRequest(
                        GoldTransferDirection.MANAGER_TO_AGENCY, "Silverkeep Guild",
                        null, managerGold + 1)));

        assertEquals(409, error.status());
        assertEquals(managerGold, managerRepository.findById(MANAGER4_ID).getGold());
        assertEquals(agencyGold, agencyRepository.findById(SILVERKEEP_ID).getGold());
    }

    @Test
    @TestTransaction
    void missingRecipientAndZeroAmountAreRejected() {
        assertEquals(404, assertThrows(GoldTransferRejectedException.class,
                () -> transferService.transfer(new GoldTransferRequest(
                        GoldTransferDirection.AGENCY_TO_MANAGER, "Dawnwatch Agency",
                        "Unknown Manager", 1))).status());
        assertEquals(400, assertThrows(GoldTransferRejectedException.class,
                () -> transferService.transfer(new GoldTransferRequest(
                        GoldTransferDirection.AGENCY_TO_MANAGER, "Dawnwatch Agency",
                        "Manager 10", 0))).status());
        assertEquals(400, assertThrows(GoldTransferRejectedException.class,
                () -> transferService.transfer(new GoldTransferRequest(
                        GoldTransferDirection.AGENCY_TO_MANAGER, "Dawnwatch Agency",
                        null, 1))).status());
    }
}
