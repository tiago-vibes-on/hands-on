package io.tiagovibeson.heroassociation.application;

import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.HeroAlreadyAssignedException;
import io.tiagovibeson.heroassociation.application.exception.HeroNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.HeroAwayException;
import io.tiagovibeson.heroassociation.application.exception.PartyNameAlreadyUsedException;
import io.tiagovibeson.heroassociation.application.exception.PartyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.PartyAwayException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import io.tiagovibeson.heroassociation.repository.PartyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PartyManagementService {

    @Inject jakarta.persistence.EntityManager em;

    @Inject
    AgencyRepository agencyRepository;

    @Inject
    PartyRepository partyRepository;

    @Inject
    HeroRepository heroRepository;

    @Inject
    AgencyStateService agencyStateService;

    @Inject
    AgencyAccessService agencyAccessService;

    @Transactional
    public AgencyStateResponse createParty(UUID agencyId, String name) {
        Agency agency = findAgency(agencyId);
        agencyAccessService.requireMembership(agencyId);
        Manager manager = agencyAccessService.currentManager();
        String trimmedName = name.trim();
        if (partyRepository.count(
                "agency.id = ?1 and ownerManager.id = ?2 and name = ?3",
                agencyId, manager.getId(), trimmedName) > 0) {
            throw new PartyNameAlreadyUsedException(trimmedName);
        }

        partyRepository.persist(new Party(agency, manager, trimmedName));
        return agencyStateService.findState(agencyId);
    }

    @Transactional
    public AgencyStateResponse addHero(UUID agencyId, UUID partyId, UUID heroId) {
        agencyAccessService.requireMembership(agencyId);
        Manager manager = agencyAccessService.currentManager();
        Party party = findParty(agencyId, partyId, manager.getId());
        Hero hero = findAssignableHero(agencyId, manager.getId(), heroId);
        validateMembershipCanChange(party, hero);

        hero.assignToParty(party);
        return agencyStateService.findState(agencyId);
    }

    @Transactional
    public AgencyStateResponse removeHero(UUID agencyId, UUID partyId, UUID heroId) {
        agencyAccessService.requireMembership(agencyId);
        Manager manager = agencyAccessService.currentManager();
        Party party = findParty(agencyId, partyId, manager.getId());
        Hero hero = heroRepository.findForUpdate(heroId)
                .filter(candidate -> candidate.getParty() != null
                        && candidate.getParty().getId().equals(partyId)
                        && (candidate.getOwnerManager() == null
                                || candidate.getOwnerManager().getId().equals(manager.getId())))
                .orElseThrow(() -> new HeroNotFoundException(heroId));
        validateMembershipCanChange(party, hero);

        if (hero.getParty() != null && hero.getParty().getId().equals(partyId)) {
            hero.removeFromParty();
        }

        return agencyStateService.findState(agencyId);
    }

    private Agency findAgency(UUID agencyId) {
        return agencyRepository.findByIdOptional(agencyId)
                .orElseThrow(() -> new AgencyNotFoundException(agencyId));
    }

    private Party findParty(UUID agencyId, UUID partyId, UUID managerId) {
        return partyRepository.findOwnedForUpdate(agencyId, partyId, managerId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
    }

    private Hero findAssignableHero(UUID agencyId, UUID managerId, UUID heroId) {
        return heroRepository.findForUpdate(heroId)
                .filter(hero -> hero.getOwnerManager() != null
                        ? hero.getOwnerManager().getId().equals(managerId)
                        : hero.getAgency() != null && hero.getAgency().getId().equals(agencyId))
                .orElseThrow(() -> new HeroNotFoundException(heroId));
    }

    private void validateMembershipCanChange(Party party, Hero hero) {
         hero.requireNoPendingAssets();
        if (isAway(party)) {
            throw new PartyAwayException(party.getId());
        }
        if (hero.getActivity() == HeroActivity.ON_EXPEDITION || (hero.getParty() != null && isAway(hero.getParty()))) {
            throw new HeroAwayException(hero.getId());
        }
        if (hero.getParty() != null && !hero.getParty().getId().equals(party.getId())) {
            throw new HeroAlreadyAssignedException(hero.getId());
        }
    }

    private boolean isAway(Party party) {
        return em.createQuery("select count(r) from ExpeditionReservation r where r.partyId = :party and r.appliedAt is null and r.releasedAt is null", Long.class)
                .setParameter("party", party.getId()).getSingleResult() > 0;
    }
}
