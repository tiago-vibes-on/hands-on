package io.tiagovibeson.heroassociation.domain.combat;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.List;

import org.junit.jupiter.api.Test;

class CombatBattleTest {

    @Test
    void shouldResolveEachCombatantsBasicAttackOnItsOwnTimer() {
        Combatant warrior = combatant(
                "warrior", CombatTeam.HEROES, 300, 50, 300, 50, 22, 1_300, 10, 2, 0, 0, 2, List.of());
        Combatant archer = combatant(
                "archer", CombatTeam.HEROES, 200, 200, 200, 200, 26, 1_100, 6, 6, 0, 0, 2, List.of());
        Combatant troll = combatant(
                "troll", CombatTeam.CREATURES, 1_000, 100, 1_000, 100, 1, 1_850, 0, 0, 0, 0, 2, List.of());

        CombatBattle battle = CombatBattle.start(List.of(warrior, archer), List.of(troll));

        List<CombatEvent> events = battle.advanceTo(2_000, () -> 0.99);

        assertThat(events.stream().map(CombatEvent::occurredAtMilliseconds).toList(), contains(480L, 650L, 760L, 1_000L, 1_750L, 1_780L));
        assertThat(events.stream().map(CombatEvent::action).toList(), contains(
                CombatAction.BASIC_ATTACK,
                CombatAction.BASIC_ATTACK,
                CombatAction.BASIC_ATTACK,
                CombatAction.RECOVERY,
                CombatAction.BASIC_ATTACK,
                CombatAction.BASIC_ATTACK));
        assertThat(troll.getCurrentHealth(), is(904));
        assertThat(warrior.getCurrentHealth(), is(300));
    }

    @Test
    void shouldResolveMageSpellsWithTheirOwnCooldownsAndManaCosts() {
        Combatant mage = combatant(
                "mage", CombatTeam.HEROES, 100, 500, 100, 500, 0, 1_700, 2, 10, 15, 0, 2,
                List.of(CombatSpell.FIRE_BALL, CombatSpell.LIGHTNING_RAIL));
        Combatant troll = combatant(
                "troll", CombatTeam.CREATURES, 1_000, 100, 1_000, 100, 0, 10_000, 0, 0, 0, 0, 2, List.of());

        CombatBattle battle = CombatBattle.start(List.of(mage), List.of(troll));

        List<CombatEvent> events = battle.advanceTo(1_350, () -> 0.99);
        List<CombatEvent> spellEvents = events.stream()
                .filter(event -> event.action() == CombatAction.FIRE_BALL || event.action() == CombatAction.LIGHTNING_RAIL)
                .toList();

        assertThat(spellEvents, hasSize(2));
        assertThat(spellEvents.stream().map(CombatEvent::action).toList(), contains(CombatAction.FIRE_BALL, CombatAction.LIGHTNING_RAIL));
        assertThat(spellEvents.getFirst().occurredAtMilliseconds(), is(900L));
        assertThat(spellEvents.getFirst().hits().getFirst().damage(), is(33));
        assertThat(spellEvents.getFirst().manaSpent(), is(20));
        assertThat(spellEvents.get(1).occurredAtMilliseconds(), is(1_350L));
        assertThat(spellEvents.get(1).hits().getFirst().damage(), is(14));
        assertThat(spellEvents.get(1).manaSpent(), is(40));
        assertThat(mage.getCurrentMana(), is(450));
    }

    @Test
    void shouldUseInjectedRandomnessForCriticalHitsAndFinishWhenAllCreaturesDie() {
        Combatant warrior = combatant(
                "warrior", CombatTeam.HEROES, 300, 50, 300, 50, 22, 1_300, 10, 2, 0, 1, 2, List.of());
        Combatant troll = combatant(
                "troll", CombatTeam.CREATURES, 40, 100, 40, 100, 1, 1_850, 0, 0, 0, 0, 2, List.of());
        CombatBattle battle = CombatBattle.start(List.of(warrior), List.of(troll));

        List<CombatEvent> events = battle.advanceTo(480, () -> 0.5);

        CombatHit hit = events.getFirst().hits().getFirst();
        assertThat(hit.damage(), is(44));
        assertThat(hit.critical(), is(true));
        assertThat(hit.defeated(), is(true));
        assertThat(battle.getStatus(), is(CombatStatus.HERO_VICTORY));
    }

    private Combatant combatant(
            String id,
            CombatTeam team,
            int maxHealth,
            int maxMana,
            int currentHealth,
            int currentMana,
            int attackDamage,
            long attackIntervalMilliseconds,
            int healthRecoveryPerSecond,
            int manaRecoveryPerSecond,
            int magicLevel,
            double criticalChance,
            double criticalDamageMultiplier,
            List<CombatSpell> spells) {
        return new Combatant(
                id,
                id,
                team,
                maxHealth,
                maxMana,
                currentHealth,
                currentMana,
                attackDamage,
                attackIntervalMilliseconds,
                healthRecoveryPerSecond,
                manaRecoveryPerSecond,
                magicLevel,
                criticalChance,
                criticalDamageMultiplier,
                spells);
    }
}
