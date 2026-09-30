package io.tiagovibeson.heroassociation.expedition;

import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.combat.CombatBattleSnapshot;
import io.tiagovibeson.heroassociation.domain.combat.CombatEvent;
import io.tiagovibeson.heroassociation.expedition.RunState.HeroState;
import io.tiagovibeson.heroassociation.expedition.RunState.Outcome;

/** A disposable, precomputed fight; only the run's current fight ID may use it. */
public record FightTimeline(UUID fightId, Outcome outcome, List<HeroState> heroes,
                            List<Window> windows) {

    public FightTimeline {
        heroes = List.copyOf(heroes);
        windows = List.copyOf(windows);
        if (windows.isEmpty() || !fightId.equals(outcome.fightId())) {
            throw new IllegalArgumentException("A timeline needs windows and a matching outcome.");
        }
    }

    public record Window(long startMilliseconds, long endMilliseconds,
                         CombatBattleSnapshot openingSnapshot, List<Event> events) {
        public Window {
            events = List.copyOf(events);
            if (startMilliseconds < 0 || endMilliseconds <= startMilliseconds
                    || openingSnapshot.currentTimeMilliseconds() != startMilliseconds) {
                throw new IllegalArgumentException("Invalid fight timeline window.");
            }
        }
    }

    public record Event(long sequenceNumber, CombatEvent combat) { }
}
