package io.tiagovibeson.heroassociation.application;

import java.util.Locale;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyNameAlreadyUsedException;
import io.tiagovibeson.heroassociation.application.exception.InvalidAgencyNameException;
import io.tiagovibeson.heroassociation.application.exception.ManagerAlreadyInAgencyException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.AgencyMember;
import io.tiagovibeson.heroassociation.domain.AgencyMemberRole;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.repository.AgencyMemberRepository;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.PartyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AgencyCreationService {

    @Inject
    AgencyAccessService agencyAccessService;

    @Inject
    AgencyRepository agencyRepository;

    @Inject
    AgencyMemberRepository agencyMemberRepository;

    @Inject
    PartyRepository partyRepository;

    @Inject
    AgencyStateService agencyStateService;

    @Transactional
    public AgencyStateResponse create(String requestedName) {
        Manager leader = agencyAccessService.currentManager();
        if (agencyMemberRepository.existsByManagerId(leader.getId())) {
            throw new ManagerAlreadyInAgencyException();
        }

        String name = normalizedName(requestedName);
        String nameNormalized = name.toLowerCase(Locale.ROOT);
        if (agencyRepository.existsWithNormalizedName(nameNormalized)) {
            throw new AgencyNameAlreadyUsedException(name);
        }

        Agency agency = new Agency(name, nameNormalized, leader);
        agencyRepository.persist(agency);
        agencyMemberRepository.persist(new AgencyMember(agency, leader, AgencyMemberRole.LEADER));
        for (Party party : partyRepository.list("ownerManager.id = ?1 and agency is null", leader.getId())) {
            party.attachToAgency(agency);
        }
        agencyRepository.flush();
        return agencyStateService.findState(agency.getId());
    }

    private String normalizedName(String requestedName) {
        if (requestedName == null) {
            throw new InvalidAgencyNameException();
        }
        String name = requestedName.trim();
        if (name.length() < 3 || name.length() > 100) {
            throw new InvalidAgencyNameException();
        }
        return name;
    }
}
