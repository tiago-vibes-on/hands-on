package io.tiagovibeson.heroassociation.expedition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.combat.CombatAction;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattleSnapshot;
import io.tiagovibeson.heroassociation.domain.combat.CombatEvent;
import io.tiagovibeson.heroassociation.domain.combat.CombatHit;
import io.tiagovibeson.heroassociation.domain.combat.CombatSpell;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;
import io.tiagovibeson.heroassociation.domain.combat.CombatantSnapshot;

/** Reads a saved plan; visual requests never run the combat engine. */
public final class FightTimelineProjector {

    private static final long RECENT_MILLISECONDS = 3_000;

    private FightTimelineProjector() { }

    public static VisualFrame at(RunState run, FightTimeline timeline, Instant now) {
        if (run.fight() == null || run.phase() != RunState.Phase.FIGHTING) {
            throw new IllegalArgumentException("Only a fighting run has a visual frame.");
        }
        UUID fightId = run.fight().fightId();
        if (timeline == null) {
            return frame(fightId, 0, CombatStatus.IN_PROGRESS,
                    run.fight().openingSnapshot(), List.of(), List.of());
        }
        if (!fightId.equals(timeline.fightId())) {
            throw new IllegalStateException("Fight plan does not match the active run.");
        }
        long elapsed = Math.min(timeline.outcome().durationMilliseconds(),
                Math.max(0, java.time.Duration.between(run.fight().startedAt(), now).toMillis()));
        FightTimeline.Window current = timeline.windows().getFirst();
        for (FightTimeline.Window window : timeline.windows()) {
            if (window.startMilliseconds() > elapsed) break;
            current = window;
        }
        long lowerBound = Math.max(0, elapsed - RECENT_MILLISECONDS);
        List<VisualEvent> recent = new ArrayList<>();
        for (FightTimeline.Window window : timeline.windows()) {
            if (window.endMilliseconds() < lowerBound) continue;
            if (window.startMilliseconds() > elapsed) break;
            for (FightTimeline.Event event : window.events()) {
                long when = event.combat().occurredAtMilliseconds();
                if (when >= lowerBound && when <= elapsed) {
                    recent.add(VisualEvent.from(event));
                }
            }
        }
        CombatStatus status = elapsed >= timeline.outcome().durationMilliseconds()
                ? timeline.outcome().status() : CombatStatus.IN_PROGRESS;
        return frame(fightId, elapsed, status, current.openingSnapshot(),
                current.events(), recent);
    }

    private static VisualFrame frame(UUID fightId, long elapsed, CombatStatus status,
                                     CombatBattleSnapshot opening,
                                     List<FightTimeline.Event> windowEvents,
                                     List<VisualEvent> recent) {
        Map<String, MutableCombatant> heroes = combatants(opening.heroes());
        Map<String, MutableCombatant> creatures = combatants(opening.creatures());
        for (FightTimeline.Event item : windowEvents) {
            CombatEvent event = item.combat();
            if (event.occurredAtMilliseconds() > elapsed) break;
            MutableCombatant actor = heroes.getOrDefault(event.actorId(), creatures.get(event.actorId()));
            if (actor != null) {
                actor.mana = Math.max(0, actor.mana - event.manaSpent());
                actor.health = Math.min(actor.maxHealth, actor.health + event.healthRecovered());
                actor.mana = Math.min(actor.maxMana, actor.mana + event.manaRecovered());
                for (CombatSpell spell : CombatSpell.values()) {
                    if (event.action() == spell.getAction()) {
                        actor.nextSpellCastAt.put(spellId(spell),
                                event.occurredAtMilliseconds() + spell.getCooldownMilliseconds());
                    }
                }
            }
            for (CombatHit hit : event.hits()) {
                MutableCombatant target = heroes.getOrDefault(hit.targetId(), creatures.get(hit.targetId()));
                if (target != null) target.health = Math.max(0, target.health - hit.damage());
            }
        }
        return new VisualFrame(fightId, elapsed, status,
                heroes.values().stream().map(MutableCombatant::snapshot).toList(),
                creatures.values().stream().map(MutableCombatant::snapshot).toList(), recent);
    }

    private static Map<String, MutableCombatant> combatants(List<CombatantSnapshot> opening) {
        Map<String, MutableCombatant> result = new LinkedHashMap<>();
        for (CombatantSnapshot combatant : opening) {
            result.put(combatant.id(), new MutableCombatant(combatant));
        }
        return result;
    }

    private static String spellId(CombatSpell spell) {
        return spell.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public record VisualFrame(UUID fightId, long elapsedMilliseconds, CombatStatus status,
                              List<VisualCombatant> heroes, List<VisualCombatant> creatures,
                              List<VisualEvent> recentEvents) { }

    public record VisualCombatant(String id, int health, int mana, int maxHealth, int maxMana,
                                  int magicLevel, Map<String, Long> nextSpellCastAt) { }

    public record VisualEvent(long sequenceNumber, long occurredAtMilliseconds,
                              CombatAction action, String actorId, List<CombatHit> hits,
                              int manaSpent, int healthRecovered, int manaRecovered) {
        static VisualEvent from(FightTimeline.Event item) {
            CombatEvent event = item.combat();
            return new VisualEvent(item.sequenceNumber(), event.occurredAtMilliseconds(),
                    event.action(), event.actorId(), event.hits(), event.manaSpent(),
                    event.healthRecovered(), event.manaRecovered());
        }
    }

    private static final class MutableCombatant {
        private final String id;
        private final int maxHealth;
        private final int maxMana;
        private final int magicLevel;
        private final Map<String, Long> nextSpellCastAt = new LinkedHashMap<>();
        private int health;
        private int mana;

        private MutableCombatant(CombatantSnapshot source) {
            id = source.id();
            maxHealth = source.maxHealth();
            maxMana = source.maxMana();
            magicLevel = source.magicLevel();
            health = source.currentHealth();
            mana = source.currentMana();
            source.nextSpellCastAt().forEach((spell, time) -> nextSpellCastAt.put(spellId(spell), time));
        }

        private VisualCombatant snapshot() {
            return new VisualCombatant(id, health, mana, maxHealth, maxMana,
                    magicLevel, Map.copyOf(nextSpellCastAt));
        }
    }
}
