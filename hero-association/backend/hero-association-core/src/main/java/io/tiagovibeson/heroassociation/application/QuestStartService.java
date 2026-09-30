package io.tiagovibeson.heroassociation.application;

import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.BorrowingFeeRejectedException;
import io.tiagovibeson.heroassociation.application.exception.HeroOnQuestException;
import io.tiagovibeson.heroassociation.application.exception.InvalidQuestPartySizeException;
import io.tiagovibeson.heroassociation.application.exception.PartyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.PartyOnQuestException;
import io.tiagovibeson.heroassociation.application.exception.QuestNotAvailableException;
import io.tiagovibeson.heroassociation.application.exception.QuestNotFoundException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.CreatureCombatProfile;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.domain.Quest;
import io.tiagovibeson.heroassociation.domain.QuestStatus;
import io.tiagovibeson.heroassociation.domain.QuestCombat;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRepository;
import io.tiagovibeson.heroassociation.repository.PartyRepository;
import io.tiagovibeson.heroassociation.repository.QuestRepository;
import io.tiagovibeson.heroassociation.repository.QuestCombatRepository;
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
    ManagerRepository managerRepository;

    @Inject
    AgencyRepository agencyRepository;

    @Inject
    QuestCombatRepository questCombatRepository;

    @Inject
    CreatureDefinitionResolver creatureDefinitionResolver;

    @Inject
    AgencyStateService agencyStateService;

    @Inject
    AgencyAccessService agencyAccessService;

    @Transactional
    public AgencyStateResponse startQuest(UUID agencyId, UUID questId, UUID partyId, long expectedBorrowingFeeGold) {
        agencyAccessService.requireMembership(agencyId);
        Manager manager = agencyAccessService.currentManager();
        Quest quest = findQuest(agencyId, questId);
        Party party = findParty(agencyId, partyId, manager.getId());
        List<Hero> heroes = heroRepository.listByPartyForUpdate(partyId);

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
                .filter(hero -> hero.getActivity() == HeroActivity.ON_QUEST || hero.getActivity() == HeroActivity.ON_EXPEDITION)
                .findFirst()
                .ifPresent(hero -> {
                    throw new HeroOnQuestException(hero.getId());
                });

        long borrowingFeeGold = totalBorrowingFee(agencyId, heroes);
        if (expectedBorrowingFeeGold != borrowingFeeGold) {
            throw new BorrowingFeeRejectedException(
                    "Borrowing fee changed: expected %d gold, current total is %d gold."
                            .formatted(expectedBorrowingFeeGold, borrowingFeeGold));
        }
        transferBorrowingFee(agencyId, manager, borrowingFeeGold);

        quest.startWith(party);
        heroes.forEach(hero -> hero.changeActivity(HeroActivity.ON_QUEST, party.getAgency().getRestLevel()));
        CreatureCombatProfile creatureProfile = creatureDefinitionResolver.resolveLatest(quest.getCreatureName());
        questCombatRepository.persist(QuestCombat.start(quest, heroes, creatureProfile));
        return agencyStateService.findState(agencyId);
    }

    private long totalBorrowingFee(UUID agencyId, List<Hero> heroes) {
        long total = 0;
        for (Hero hero : heroes) {
            if (hero.getOwnerManager() != null) {
                continue;
            }
            if (hero.getAgency() == null || !hero.getAgency().getId().equals(agencyId)) {
                throw new BorrowingFeeRejectedException("A party hero is not owned by this agency.");
            }
            if (hero.getBorrowingFeeGold() > Long.MAX_VALUE - total) {
                throw new BorrowingFeeRejectedException("Borrowing fee total is too large.");
            }
            total += hero.getBorrowingFeeGold();
        }
        return total;
    }

    private void transferBorrowingFee(UUID agencyId, Manager manager, long feeGold) {
        if (feeGold == 0) {
            return;
        }
        Manager payer = managerRepository.findForUpdate(manager.getId())
                .orElseThrow(IllegalStateException::new);
        if (payer.getGold() < feeGold) {
            throw new BorrowingFeeRejectedException(
                    "Insufficient personal gold: need %d gold, have %d gold."
                            .formatted(feeGold, payer.getGold()));
        }
        Agency agency = agencyRepository.findForUpdate(agencyId)
                .orElseThrow(() -> new AgencyNotFoundException(agencyId));
        if (agency.getGold() > Long.MAX_VALUE - feeGold) {
            throw new BorrowingFeeRejectedException("Agency gold balance cannot hold the borrowing fee.");
        }
        payer.decreaseGold(feeGold);
        agency.increaseGold(feeGold);
    }

    private Quest findQuest(UUID agencyId, UUID questId) {
        return questRepository.findForStart(agencyId, questId)
                .orElseThrow(() -> new QuestNotFoundException(questId));
    }

    private Party findParty(UUID agencyId, UUID partyId, UUID managerId) {
        return partyRepository.findOwnedForUpdate(agencyId, partyId, managerId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
    }

    private boolean isOnQuest(Party party) {
        return party.getQuest() != null && party.getQuest().getStatus() == QuestStatus.IN_PROGRESS;
    }
}
