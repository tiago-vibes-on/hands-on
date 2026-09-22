package io.tiagovibeson.heroassociation.application;

import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyNotFoundException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.AgencyRune;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.domain.Quest;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.AgencyRuneRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import io.tiagovibeson.heroassociation.repository.PartyRepository;
import io.tiagovibeson.heroassociation.repository.QuestRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AgencyStateService {

    @Inject
    AgencyRepository agencyRepository;

    @Inject
    HeroRepository heroRepository;

    @Inject
    PartyRepository partyRepository;

    @Inject
    QuestRepository questRepository;

    @Inject
    AgencyRuneRepository agencyRuneRepository;

    @Transactional
    public AgencyStateResponse findState(UUID agencyId) {
        Agency agency = agencyRepository.findByIdOptional(agencyId)
                .orElseThrow(() -> new AgencyNotFoundException(agencyId));
        List<Hero> heroes = heroRepository.list("agency.id = ?1 order by id", agencyId);
        List<Party> parties = partyRepository.list("agency.id = ?1 order by id", agencyId);
        List<Quest> quests = questRepository.list("agency.id = ?1 order by id", agencyId);
        List<AgencyRune> runeInventory = agencyRuneRepository.list("agency.id = ?1 order by rune.id", agencyId);
        return AgencyStateResponse.from(agency, heroes, parties, quests, runeInventory);
    }
}
