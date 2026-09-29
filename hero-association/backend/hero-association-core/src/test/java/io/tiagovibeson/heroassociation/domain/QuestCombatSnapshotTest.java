package io.tiagovibeson.heroassociation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.domain.combat.CombatTeam;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import io.tiagovibeson.heroassociation.repository.QuestRepository;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class QuestCombatSnapshotTest {

    private static final UUID MOONWEAVER_ID = UUID.fromString("019c4c00-0010-7000-8000-000000000002");
    private static final UUID EMBERVEIL_ID = UUID.fromString("019c4c00-0010-7000-8000-000000000005");
    private static final UUID TROLL_QUEST_ID = UUID.fromString("019c4c00-0003-7000-8000-000000000001");

    @Inject
    HeroRepository heroRepository;

    @Inject
    QuestRepository questRepository;

    @Test
    @TestTransaction
    void shouldApplyEquippedCriticalRunesToANewCombatSnapshot() {
        Hero moonweaver = heroRepository.findById(MOONWEAVER_ID);
        Quest trollQuest = questRepository.findById(TROLL_QUEST_ID);

        QuestCombat combat = QuestCombat.start(trollQuest, List.of(moonweaver));
        QuestCombatant moonweaverSnapshot = combat.getCombatants().stream()
                .filter(combatant -> combatant.getTeam() == CombatTeam.HEROES)
                .findFirst()
                .orElseThrow();

        assertEquals(0.01, moonweaverSnapshot.getCriticalChance(), 0.000_001);
        assertEquals(2.1, moonweaverSnapshot.getCriticalDamageMultiplier(), 0.000_001);
    }

    @Test
    @TestTransaction
    void shouldKeepAnUntrainedMageAtMagicLevelOneWithoutSpells() {
        Hero emberveil = heroRepository.findById(EMBERVEIL_ID);
        Quest trollQuest = questRepository.findById(TROLL_QUEST_ID);

        QuestCombat combat = QuestCombat.start(trollQuest, List.of(emberveil));
        QuestCombatant mageSnapshot = combat.getCombatants().stream()
                .filter(combatant -> combatant.getTeam() == CombatTeam.HEROES)
                .findFirst()
                .orElseThrow();

        assertEquals(1, emberveil.getMagicLevel());
        assertEquals(1, mageSnapshot.getMagicLevel());
        assertNull(mageSnapshot.getFireBallNextCastAt());
        assertNull(mageSnapshot.getLightningRailNextCastAt());
    }
}
