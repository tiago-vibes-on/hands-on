package io.tiagovibeson.heroassociation.application.combat;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.QuestCombat;
import io.tiagovibeson.heroassociation.domain.QuestCombatant;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattle;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattleSnapshot;
import io.tiagovibeson.heroassociation.domain.combat.CombatSpell;
import io.tiagovibeson.heroassociation.domain.combat.CombatantSnapshot;
import io.tiagovibeson.heroassociation.domain.combat.CombatTeam;

public final class QuestCombatSnapshotMapper {

    private QuestCombatSnapshotMapper() {
    }

    public static CombatBattle toBattle(QuestCombat combat) {
        List<CombatantSnapshot> heroes = combat.getCombatants().stream()
                .filter(combatant -> combatant.getTeam() == CombatTeam.HEROES)
                .map(QuestCombatSnapshotMapper::toSnapshot)
                .toList();
        List<CombatantSnapshot> creatures = combat.getCombatants().stream()
                .filter(combatant -> combatant.getTeam() == CombatTeam.CREATURES)
                .map(QuestCombatSnapshotMapper::toSnapshot)
                .toList();
        return CombatBattle.restore(new CombatBattleSnapshot(
                combat.getCurrentTimeMilliseconds(),
                combat.getNextRecoveryAt(),
                combat.getStatus(),
                heroes,
                creatures));
    }

    public static void apply(QuestCombat combat, CombatBattle battle, Instant synchronizedAt) {
        combat.apply(battle.snapshot(), synchronizedAt);
    }

    private static CombatantSnapshot toSnapshot(QuestCombatant combatant) {
        Map<CombatSpell, Long> nextSpellCastAt = new EnumMap<>(CombatSpell.class);
        if (combatant.getFireBallNextCastAt() != null) {
            nextSpellCastAt.put(CombatSpell.FIRE_BALL, combatant.getFireBallNextCastAt());
        }
        if (combatant.getLightningRailNextCastAt() != null) {
            nextSpellCastAt.put(CombatSpell.LIGHTNING_RAIL, combatant.getLightningRailNextCastAt());
        }
        return new CombatantSnapshot(
                combatant.getId().toString(),
                combatant.getName(),
                combatant.getTeam(),
                combatant.getMaxHealth(),
                combatant.getMaxMana(),
                combatant.getCurrentHealth(),
                combatant.getCurrentMana(),
                combatant.getAttackDamage(),
                combatant.getAttackIntervalMilliseconds(),
                combatant.getHealthRecoveryPerSecond(),
                combatant.getManaRecoveryPerSecond(),
                combatant.getMagicLevel(),
                combatant.getCriticalChance(),
                combatant.getCriticalDamageMultiplier(),
                spellsFor(combatant.getHeroClass()),
                combatant.getNextBasicAttackAt(),
                nextSpellCastAt);
    }

    private static List<CombatSpell> spellsFor(HeroClass heroClass) {
        return heroClass == HeroClass.MAGE ? List.of(CombatSpell.values()) : List.of();
    }
}
