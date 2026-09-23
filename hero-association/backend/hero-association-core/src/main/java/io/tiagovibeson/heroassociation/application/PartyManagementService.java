package io.tiagovibeson.heroassociation.application;

import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.HeroNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.HeroOnQuestException;
import io.tiagovibeson.heroassociation.application.exception.PartyNameAlreadyUsedException;
import io.tiagovibeson.heroassociation.application.exception.PartyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.PartyOnQuestException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.domain.QuestStatus;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import io.tiagovibeson.heroassociation.repository.PartyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PartyManagementService {

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
        String trimmedName = name.trim();
        if (partyRepository.count("agency.id = ?1 and name = ?2", agencyId, trimmedName) > 0) {
            throw new PartyNameAlreadyUsedException(trimmedName);
        }

        partyRepository.persist(new Party(agency, trimmedName));
        return agencyStateService.findState(agencyId);
    }

    @Transactional
    public AgencyStateResponse addHero(UUID agencyId, UUID partyId, UUID heroId) {
        agencyAccessService.requireMembership(agencyId);
        Party party = findParty(agencyId, partyId);
        Hero hero = findHero(agencyId, heroId);
        validateMembershipCanChange(party, hero);

        hero.assignToParty(party);
        return agencyStateService.findState(agencyId);
    }

    @Transactional
    public AgencyStateResponse removeHero(UUID agencyId, UUID partyId, UUID heroId) {
        agencyAccessService.requireMembership(agencyId);
        Party party = findParty(agencyId, partyId);
        Hero hero = findHero(agencyId, heroId);
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

    private Party findParty(UUID agencyId, UUID partyId) {
        return partyRepository.find("id = ?1 and agency.id = ?2", partyId, agencyId)
                .firstResultOptional()
                .orElseThrow(() -> new PartyNotFoundException(partyId));
    }

    private Hero findHero(UUID agencyId, UUID heroId) {
        return heroRepository.find("id = ?1 and agency.id = ?2", heroId, agencyId)
                .firstResultOptional()
                .orElseThrow(() -> new HeroNotFoundException(heroId));
    }

    private void validateMembershipCanChange(Party party, Hero hero) {
        if (isOnQuest(party)) {
            throw new PartyOnQuestException(party.getId());
        }
        if (hero.getActivity() == HeroActivity.ON_QUEST || (hero.getParty() != null && isOnQuest(hero.getParty()))) {
            throw new HeroOnQuestException(hero.getId());
        }
    }

    private boolean isOnQuest(Party party) {
        return party.getQuest() != null && party.getQuest().getStatus() == QuestStatus.IN_PROGRESS;
    }
}
