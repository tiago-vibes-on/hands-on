package io.tiagovibeson.heroassociation.application;

import java.util.Locale;

import io.tiagovibeson.heroassociation.api.v1.gold.GoldTransferRequest;
import io.tiagovibeson.heroassociation.api.v1.gold.GoldTransferResponse;
import io.tiagovibeson.heroassociation.application.exception.GoldTransferRejectedException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.GoldTransferDirection;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class GoldTransferService {

    @Inject
    AgencyAccessService agencyAccessService;

    @Inject
    AgencyRepository agencyRepository;

    @Inject
    ManagerRepository managerRepository;

    @Transactional
    public GoldTransferResponse transfer(GoldTransferRequest request) {
        if (request == null || request.direction() == null || request.amountGold() == null) {
            throw rejected(400, "A transfer direction and a positive whole gold amount are required.");
        }
        long amountGold;
        try {
            amountGold = request.amountGold().longValueExact();
        } catch (ArithmeticException exception) {
            throw rejected(400, "Gold amount must be a positive whole number within the supported range.");
        }
        if (amountGold <= 0) {
            throw rejected(400, "A transfer direction and a positive whole gold amount are required.");
        }

        String agencyName = normalizedName(request.agencyName(), "An agency name is required.");
        Agency destinationAgency = agencyRepository.findByNormalizedName(agencyName)
                .orElseThrow(() -> rejected(404, "The requested agency was not found."));

        if (request.direction() == GoldTransferDirection.MANAGER_TO_AGENCY) {
            if (request.managerName() != null && !request.managerName().isBlank()) {
                throw rejected(400, "A Manager-to-agency transfer must not specify a recipient Manager.");
            }
            Manager actor = agencyAccessService.currentManager();
            Manager manager = managerRepository.findForUpdate(actor.getId())
                    .orElseThrow(() -> rejected(404, "The current Manager was not found."));
            Agency agency = agencyRepository.findForUpdate(destinationAgency.getId())
                    .orElseThrow(() -> rejected(404, "The requested agency was not found."));
            if (manager.getGold() < amountGold) {
                throw rejected(409, "The Manager does not have enough gold.");
            }
            if (agency.getGold() > Long.MAX_VALUE - amountGold) {
                throw rejected(409, "The agency cannot receive that much gold.");
            }
            manager.decreaseGold(amountGold);
            agency.increaseGold(amountGold);
            return GoldTransferResponse.from(request.direction(), amountGold, agency, manager);
        }

        String managerName = normalizedName(request.managerName(), "A recipient Manager name is required.");
        agencyAccessService.requireLeadership(destinationAgency.getId());
        Manager recipient = managerRepository.findByNormalizedDisplayName(managerName)
                .orElseThrow(() -> rejected(404, "The recipient Manager was not found."));
        // Both directions lock Manager before Agency to avoid transfer-to-transfer deadlocks.
        Manager manager = managerRepository.findForUpdate(recipient.getId())
                .orElseThrow(() -> rejected(404, "The recipient Manager was not found."));
        Agency agency = agencyRepository.findForUpdate(destinationAgency.getId())
                .orElseThrow(() -> rejected(404, "The requested agency was not found."));
        if (agency.getGold() < amountGold) {
            throw rejected(409, "The agency does not have enough gold.");
        }
        if (manager.getGold() > Long.MAX_VALUE - amountGold) {
            throw rejected(409, "The Manager cannot receive that much gold.");
        }
        agency.decreaseGold(amountGold);
        manager.increaseGold(amountGold);
        return GoldTransferResponse.from(request.direction(), amountGold, agency, manager);
    }

    private String normalizedName(String requestedName, String missingMessage) {
        if (requestedName == null || requestedName.isBlank()) {
            throw rejected(400, missingMessage);
        }
        return requestedName.trim().toLowerCase(Locale.ROOT);
    }

    private GoldTransferRejectedException rejected(int status, String message) {
        return new GoldTransferRejectedException(status, message);
    }
}
