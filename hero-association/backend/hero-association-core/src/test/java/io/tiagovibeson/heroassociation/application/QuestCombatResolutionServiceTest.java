package io.tiagovibeson.heroassociation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.Quest;
import io.tiagovibeson.heroassociation.domain.QuestCombat;
import io.tiagovibeson.heroassociation.domain.QuestStatus;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import io.tiagovibeson.heroassociation.repository.QuestCombatRepository;
import io.tiagovibeson.heroassociation.repository.QuestRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class QuestCombatResolutionServiceTest {

    private static final UUID TROLL_COMBAT_ID = UUID.fromString("019c4c00-0050-7000-8000-000000000001");

    @Inject
    QuestCombatProgressionService questCombatProgressionService;

    @Inject
    QuestCombatRepository questCombatRepository;

    @Inject
    QuestRepository questRepository;

    @Inject
    HeroRepository heroRepository;

    @Inject
    EntityManager entityManager;

    @Test
    @TestTransaction
    void shouldCompleteTheQuestAndReleaseItsPartyWhenHeroesWin() {
        setCombatantHealth("CREATURES", 1);
        entityManager.clear();

        QuestCombat combat = questCombatRepository.findById(TROLL_COMBAT_ID);
        UUID partyId = combat.getQuest().getParty().getId();
        Instant finishedAt = combat.getLastSynchronizedAt().plusSeconds(2);

        questCombatProgressionService.synchronize(combat, finishedAt);
        entityManager.flush();
        entityManager.clear();

        Quest resolvedQuest = questRepository.findById(combat.getQuest().getId());
        assertEquals(CombatStatus.HERO_VICTORY, questCombatRepository.findById(TROLL_COMBAT_ID).getStatus());
        assertEquals(QuestStatus.COMPLETED, resolvedQuest.getStatus());
        assertEquals(finishedAt, resolvedQuest.getFinishedAt());
        assertEquals(resolvedQuest.getCreaturesRequired(), resolvedQuest.getCreaturesDefeated());
        assertNull(resolvedQuest.getParty());
        assertHeroesReturnedToTraining(partyId);
    }

    @Test
    @TestTransaction
    void shouldFailTheQuestAndReleaseItsPartyWhenCreaturesWin() {
        entityManager.createNativeQuery("""
                UPDATE quest_combat
                SET status = 'CREATURE_VICTORY'
                WHERE id = :combatId
                """)
                .setParameter("combatId", TROLL_COMBAT_ID)
                .executeUpdate();
        entityManager.clear();

        QuestCombat combat = questCombatRepository.findById(TROLL_COMBAT_ID);
        UUID partyId = combat.getQuest().getParty().getId();
        Instant finishedAt = combat.getLastSynchronizedAt().plusSeconds(1);

        questCombatProgressionService.synchronize(combat, finishedAt);
        entityManager.flush();
        entityManager.clear();

        Quest resolvedQuest = questRepository.findById(combat.getQuest().getId());
        assertEquals(CombatStatus.CREATURE_VICTORY, questCombatRepository.findById(TROLL_COMBAT_ID).getStatus());
        assertEquals(QuestStatus.FAILED, resolvedQuest.getStatus());
        assertEquals(finishedAt, resolvedQuest.getFinishedAt());
        assertNull(resolvedQuest.getParty());
        assertHeroesReturnedToTraining(partyId);
    }

    private void setCombatantHealth(String team, int health) {
        entityManager.createNativeQuery("""
                UPDATE quest_combatant
                SET current_health = :health
                WHERE combat_id = :combatId
                  AND team = :team
                """)
                .setParameter("health", health)
                .setParameter("combatId", TROLL_COMBAT_ID)
                .setParameter("team", team)
                .executeUpdate();
    }

    private void assertHeroesReturnedToTraining(UUID partyId) {
        List<Hero> heroes = heroRepository.list("party.id = ?1", partyId);
        assertTrue(heroes.stream().allMatch(hero -> hero.getActivity() == HeroActivity.TRAINING));
    }
}
