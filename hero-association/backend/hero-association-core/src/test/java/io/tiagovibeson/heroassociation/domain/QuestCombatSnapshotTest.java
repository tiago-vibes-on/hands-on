package io.tiagovibeson.heroassociation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;

import io.quarkus.test.TestTransaction;
import io.tiagovibeson.heroassociation.application.combat.QuestCombatSnapshotMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.domain.combat.CombatTeam;
import io.tiagovibeson.heroassociation.repository.CreatureDefinitionRepository;
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
    @Inject io.tiagovibeson.heroassociation.application.assets.AssetsClient assets;

    @Inject
    QuestRepository questRepository;

    @Inject
    CreatureDefinitionRepository creatureDefinitionRepository;

    @Test
    @TestTransaction
    void shouldApplyEquippedCriticalRunesToANewCombatSnapshot() {
        Hero moonweaver = heroRepository.findById(MOONWEAVER_ID);
        assets.decorate(List.of(moonweaver), assets.snapshot(List.of(), List.of(moonweaver.getId())).heroes());
        Quest trollQuest = questRepository.findById(TROLL_QUEST_ID);

        QuestCombat combat = QuestCombat.start(trollQuest, List.of(moonweaver),
                CreatureCombatProfile.from(creatureDefinitionRepository.findLatestByName("Troll").orElseThrow()));
        QuestCombatant moonweaverSnapshot = combat.getCombatants().stream()
                .filter(combatant -> combatant.getTeam() == CombatTeam.HEROES)
                .findFirst()
                .orElseThrow();

        assertEquals(0.01, moonweaverSnapshot.getCriticalChance(), 0.000_001);
        assertEquals(2.1, moonweaverSnapshot.getCriticalDamageMultiplier(), 0.000_001);
        assertEquals(3, moonweaverSnapshot.getRuneSnapshots().size());
        QuestCombatantRune pinnedChanceRune = moonweaverSnapshot.getRuneSnapshots().stream()
                .filter(rune -> rune.getSlotIndex() == 1)
                .findFirst()
                .orElseThrow();
        assertEquals("critical-chance-rune", pinnedChanceRune.getRuneCode());
        assertEquals(RuneEffect.CRITICAL_CHANCE, pinnedChanceRune.getEffect());
        HeroRune equippedChanceRune = moonweaver.getRuneSlots().stream()
                .filter(slot -> slot.getSlotIndex() == 1)
                .findFirst()
                .orElseThrow();
        Rune otherRune = moonweaver.getRuneSlots().stream()
                .filter(slot -> slot.getSlotIndex() == 2)
                .findFirst()
                .orElseThrow()
                .getRune();
        moonweaver.replaceRuneSnapshot(List.of(new HeroRune(moonweaver, otherRune, equippedChanceRune.getSlotIndex())));
        assertEquals("critical-chance-rune", pinnedChanceRune.getRuneCode());
        assertEquals(0.01, moonweaverSnapshot.getCriticalChance(), 0.000_001);
        assertEquals(1, moonweaverSnapshot.getHeroLevel());
        assertEquals(1, moonweaverSnapshot.getMeleeLevel());
        assertEquals(1, moonweaverSnapshot.getDistanceLevel());
        assertEquals(1, moonweaverSnapshot.getShieldLevel());
        assertEquals(15, moonweaverSnapshot.getMagicLevel());
        assertEquals(moonweaver.getStaminaMilliseconds(), moonweaverSnapshot.getStartingStaminaMilliseconds());
        assertEquals(20, moonweaverSnapshot.getBasicAttackManaCost());
    }

    @Test
    @TestTransaction
    void shouldKeepAnUntrainedMageAtMagicLevelOneWithoutSpells() {
        Hero emberveil = heroRepository.findById(EMBERVEIL_ID);
        Quest trollQuest = questRepository.findById(TROLL_QUEST_ID);

        QuestCombat combat = QuestCombat.start(trollQuest, List.of(emberveil),
                CreatureCombatProfile.from(creatureDefinitionRepository.findLatestByName("Troll").orElseThrow()));
        QuestCombatant mageSnapshot = combat.getCombatants().stream()
                .filter(combatant -> combatant.getTeam() == CombatTeam.HEROES)
                .findFirst()
                .orElseThrow();

        assertEquals(1, emberveil.getMagicLevel());
        assertEquals(1, mageSnapshot.getMagicLevel());
        assertNull(mageSnapshot.getFireBallNextCastAt());
        assertNull(mageSnapshot.getLightningRailNextCastAt());
    }

    @Test
    @TestTransaction
    void shouldPinTheSeededTrollDefinitionInANewBattle() {
        CreatureDefinition troll = creatureDefinitionRepository.findLatestByName("Troll").orElseThrow();
        QuestCombat combat = QuestCombat.start(questRepository.findById(TROLL_QUEST_ID),
                List.of(heroRepository.findById(MOONWEAVER_ID)), CreatureCombatProfile.from(troll));
        QuestCombatant creature = combat.getCombatants().stream()
                .filter(combatant -> combatant.getTeam() == CombatTeam.CREATURES)
                .findFirst()
                .orElseThrow();

        assertEquals(troll.getId(), creature.getCreatureDefinitionId());
        assertEquals(1, creature.getCreatureDefinitionVersion());
        assertEquals(2_000, creature.getMaxHealth());
        assertEquals(100, creature.getBaseExperience());
        assertEquals(0.1, creature.getCriticalChance(), 0.000_001);
    }
    @Test
    @TestTransaction
    void shouldRestoreTheSeededMageUsingPinnedManaCost() {
        QuestCombat seededBattle = questRepository.findById(TROLL_QUEST_ID).getCombat();
        var mage = QuestCombatSnapshotMapper.toBattle(seededBattle).snapshot().heroes().stream()
                .filter(hero -> hero.name().equals("Moonweaver"))
                .findFirst()
                .orElseThrow();

        assertEquals(20, mage.basicAttackManaCost());
        QuestCombatant seededMage = seededBattle.getCombatants().stream()
                .filter(combatant -> combatant.getHero() != null
                        && combatant.getHero().getId().equals(MOONWEAVER_ID))
                .findFirst()
                .orElseThrow();
        assertEquals(3, seededMage.getRuneSnapshots().size());
    }
}
