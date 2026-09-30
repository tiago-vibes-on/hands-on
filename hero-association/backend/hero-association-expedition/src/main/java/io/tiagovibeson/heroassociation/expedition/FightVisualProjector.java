package io.tiagovibeson.heroassociation.expedition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.combat.CombatBattle;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattleSnapshot;
import io.tiagovibeson.heroassociation.domain.combat.CombatEvent;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;
import io.tiagovibeson.heroassociation.domain.combat.CombatantSnapshot;
import io.tiagovibeson.heroassociation.expedition.RunState.FightState;

/** Disposable visual replay from the pinned opening state; never a source of progression. */
public final class FightVisualProjector {

    private static final long STEP_MILLISECONDS = 1_000;
    private static final long MAX_FIGHT_MILLISECONDS = 30 * 60 * 1_000L;
    private static final int MAX_EVENTS = 100_000;

    private FightVisualProjector() { }

    public static VisualFrame at(RunState run, Instant now) {
        FightState fight = run.fight();
        if (run.phase() != RunState.Phase.FIGHTING || fight == null
                || !"core-v1".equals(fight.rulesetVersion())
                || !"java-random-v1".equals(fight.rngVersion())) {
            throw new IllegalArgumentException("Only a supported fighting run can be projected.");
        }
        long elapsed = Math.min(MAX_FIGHT_MILLISECONDS,
                Math.max(0, java.time.Duration.between(fight.startedAt(), now).toMillis()));
        CombatBattle battle = CombatBattle.restore(fight.openingSnapshot());
        Random random = new Random(fight.randomSeed());
        List<CombatEvent> recent = new ArrayList<>();
        long cursor = 0;
        int totalEvents = 0;
        while (cursor < elapsed && battle.getStatus() == CombatStatus.IN_PROGRESS) {
            long target = Math.min(elapsed, cursor + STEP_MILLISECONDS);
            List<CombatEvent> events = battle.advanceTo(target, random::nextDouble);
            totalEvents = Math.addExact(totalEvents, events.size());
            if (totalEvents > MAX_EVENTS) {
                throw new IllegalStateException("Fight exceeded the bounded visual replay budget.");
            }
            if (target >= elapsed - STEP_MILLISECONDS) recent.addAll(events);
            cursor = target;
        }
        long lowerBound = Math.max(0, elapsed - STEP_MILLISECONDS);
        List<CombatEvent> visible = recent.stream()
                .filter(event -> event.occurredAtMilliseconds() >= lowerBound)
                .toList();
        CombatBattleSnapshot snapshot = battle.snapshot();
        return new VisualFrame(fight.fightId(), elapsed, snapshot.status(),
                snapshot.heroes().stream().map(VisualCombatant::from).toList(),
                snapshot.creatures().stream().map(VisualCombatant::from).toList(), visible);
    }

    public record VisualFrame(UUID fightId, long elapsedMilliseconds, CombatStatus status,
                              List<VisualCombatant> heroes, List<VisualCombatant> creatures,
                              List<CombatEvent> recentEvents) { }

    public record VisualCombatant(String id, int health, int mana, int maxHealth, int maxMana) {
        static VisualCombatant from(CombatantSnapshot combatant) {
            return new VisualCombatant(combatant.id(), combatant.currentHealth(),
                    combatant.currentMana(), combatant.maxHealth(), combatant.maxMana());
        }
    }
}
