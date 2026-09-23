package io.tiagovibeson.heroassociation.application;

import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.HeroOnQuestException;
import io.tiagovibeson.heroassociation.application.exception.InvalidQuestPartySizeException;
import io.tiagovibeson.heroassociation.application.exception.PartyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.PartyOnQuestException;
import io.tiagovibeson.heroassociation.application.exception.QuestNotAvailableException;
import io.tiagovibeson.heroassociation.application.exception.QuestNotFoundException;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.domain.Quest;
import io.tiagovibeson.heroassociation.domain.QuestStatus;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import io.tiagovibeson.heroassociation.repository.PartyRepository;
import io.tiagovibeson.heroassociation.repository.QuestRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class QuestStartService {

    @Inject
    QuestRepository questRepository;

    @Inject
    PartyRepository partyRepository;

    @Inject
    HeroRepository heroRepository;

    @Inject
    AgencyStateService agencyStateService;

    @Inject
    AgencyAccessService agencyAccessService;

    @Transactional
    public AgencyStateResponse startQuest(UUID agencyId, UUID questId, UUID partyId) {
        agencyAccessService.requireMembership(agencyId);
        Quest quest = findQuest(agencyId, questId);
        Party party = findParty(agencyId, partyId);
        List<Hero> heroes = heroRepository.list("party.id = ?1 order by id", partyId);

        if (quest.getStatus() != QuestStatus.AVAILABLE) {
            throw new QuestNotAvailableException(questId);
        }
        if (isOnQuest(party)) {
            throw new PartyOnQuestException(partyId);
        }
        if (heroes.size() < quest.getMinimumHeroes() || heroes.size() > quest.getMaximumHeroes()) {
            throw new InvalidQuestPartySizeException(quest.getMinimumHeroes(), quest.getMaximumHeroes());
        }
        heroes.stream()
                .filter(hero -> hero.getActivity() == HeroActivity.ON_QUEST)
                .findFirst()
                .ifPresent(hero -> {
                    throw new HeroOnQuestException(hero.getId());
                });

        quest.startWith(party);
        heroes.forEach(hero -> hero.changeActivity(HeroActivity.ON_QUEST));
        return agencyStateService.findState(agencyId);
    }

    private Quest findQuest(UUID agencyId, UUID questId) {
        return questRepository.find("id = ?1 and agency.id = ?2", questId, agencyId)
                .firstResultOptional()
                .orElseThrow(() -> new QuestNotFoundException(questId));
    }

    private Party findParty(UUID agencyId, UUID partyId) {
        return partyRepository.find("id = ?1 and agency.id = ?2", partyId, agencyId)
                .firstResultOptional()
                .orElseThrow(() -> new PartyNotFoundException(partyId));
    }

    private boolean isOnQuest(Party party) {
        return party.getQuest() != null && party.getQuest().getStatus() == QuestStatus.IN_PROGRESS;
    }
}
