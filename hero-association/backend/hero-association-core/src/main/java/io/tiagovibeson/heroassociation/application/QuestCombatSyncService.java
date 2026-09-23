package io.tiagovibeson.heroassociation.application;

import java.time.Instant;
import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.QuestCombatNotAvailableException;
import io.tiagovibeson.heroassociation.application.exception.QuestNotFoundException;
import io.tiagovibeson.heroassociation.domain.Quest;
import io.tiagovibeson.heroassociation.domain.QuestCombat;
import io.tiagovibeson.heroassociation.repository.QuestCombatRepository;
import io.tiagovibeson.heroassociation.repository.QuestRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class QuestCombatSyncService {

    @Inject
    QuestRepository questRepository;

    @Inject
    AgencyStateService agencyStateService;

    @Inject
    QuestCombatRepository questCombatRepository;

    @Inject
    QuestCombatProgressionService questCombatProgressionService;

    @Inject
    AgencyAccessService agencyAccessService;

    @Transactional
    public AgencyStateResponse synchronize(UUID agencyId, UUID questId) {
        agencyAccessService.requireMembership(agencyId);
        Quest quest = questRepository.find("id = ?1 and agency.id = ?2", questId, agencyId)
                .firstResultOptional()
                .orElseThrow(() -> new QuestNotFoundException(questId));
        QuestCombat combat = questCombatRepository.findForUpdate(agencyId, questId)
                .orElseThrow(() -> new QuestCombatNotAvailableException(questId));

        questCombatProgressionService.synchronize(combat, Instant.now());

        return agencyStateService.findState(agencyId);
    }
}
