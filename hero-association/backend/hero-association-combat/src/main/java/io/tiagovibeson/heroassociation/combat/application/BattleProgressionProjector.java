package io.tiagovibeson.heroassociation.combat.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.tiagovibeson.heroassociation.combat.api.CreatureBattleInput;
import io.tiagovibeson.heroassociation.combat.api.HeroBattleInput;
import io.tiagovibeson.heroassociation.combat.api.StartBattleRequest;
import io.tiagovibeson.heroassociation.combat.domain.ProgressionFact;
import io.tiagovibeson.heroassociation.combat.domain.ProgressionFactType;
import io.tiagovibeson.heroassociation.domain.combat.CombatAction;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattleSnapshot;
import io.tiagovibeson.heroassociation.domain.combat.CombatEvent;
import io.tiagovibeson.heroassociation.domain.combat.CombatHit;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;

final class BattleProgressionProjector {

    private BattleProgressionProjector() {
    }

    static List<ProgressionFact> project(
            StartBattleRequest request,
            CombatBattleSnapshot before,
            List<CombatEvent> events,
            CombatBattleSnapshot after,
            long firstSequence) {
        Map<String, UUID> heroIdsByCombatant = new LinkedHashMap<>();
        Set<UUID> livingHeroes = new LinkedHashSet<>();
        for (int index = 0; index < request.heroInputs().size(); index++) {
            HeroBattleInput hero = request.heroInputs().get(index);
            heroIdsByCombatant.put(hero.combatantId().toString(), hero.heroId());
            if (before.heroes().get(index).currentHealth() > 0) {
                livingHeroes.add(hero.heroId());
            }
        }
        Map<String, Integer> creatureExperience = new LinkedHashMap<>();
        for (CreatureBattleInput creature : request.creatureInputs()) {
            creatureExperience.put(creature.combatantId().toString(), creature.baseExperience());
        }

        List<ProgressionFact> facts = new ArrayList<>();
        long processedAt = before.currentTimeMilliseconds();
        long activeUntil = after.status() == CombatStatus.IN_PROGRESS
                ? after.currentTimeMilliseconds()
                : events.getLast().occurredAtMilliseconds();
        for (CombatEvent event : events) {
            long occurredAt = event.occurredAtMilliseconds();
            if (occurredAt < processedAt || occurredAt > activeUntil) {
                throw new IllegalStateException("Engine events are outside the active interval.");
            }
            appendStamina(facts, firstSequence, livingHeroes, processedAt, occurredAt);
            processedAt = occurredAt;

            UUID actingHero = heroIdsByCombatant.get(event.actorId());
            boolean attacksCreature = event.hits().stream()
                    .anyMatch(hit -> creatureExperience.containsKey(hit.targetId()));
            if (actingHero != null
                    && (event.manaSpent() > 0
                    || event.action() == CombatAction.BASIC_ATTACK && attacksCreature)) {
                append(facts, firstSequence, occurredAt, ProgressionFactType.HERO_ACTION,
                        List.of(), actingHero, null, 0, event.action(), event.manaSpent(), 0, null);
            }
            for (CombatHit hit : event.hits()) {
                if (!hit.defeated()) {
                    continue;
                }
                Integer baseExperience = creatureExperience.get(hit.targetId());
                if (baseExperience != null) {
                    append(facts, firstSequence, occurredAt, ProgressionFactType.CREATURE_KILLED,
                            List.copyOf(livingHeroes), null, UUID.fromString(hit.targetId()),
                            0, null, 0, baseExperience, null);
                } else {
                    UUID fallenHero = heroIdsByCombatant.get(hit.targetId());
                    if (fallenHero == null) {
                        throw new IllegalStateException("Engine hit references an unknown combatant.");
                    }
                    livingHeroes.remove(fallenHero);
                    append(facts, firstSequence, occurredAt, ProgressionFactType.HERO_FELL,
                            List.of(), fallenHero, null, 0, null, 0, 0, null);
                }
            }
        }
        appendStamina(facts, firstSequence, livingHeroes, processedAt, activeUntil);
        if (after.status() != CombatStatus.IN_PROGRESS) {
            append(facts, firstSequence, activeUntil, ProgressionFactType.BATTLE_COMPLETED,
                    List.of(), null, null, 0, null, 0, 0, after);
        }
        return List.copyOf(facts);
    }

    private static void appendStamina(
            List<ProgressionFact> facts, long firstSequence, Set<UUID> livingHeroes, long from, long to) {
        if (to > from && !livingHeroes.isEmpty()) {
            append(facts, firstSequence, to, ProgressionFactType.STAMINA_ELAPSED,
                    List.copyOf(livingHeroes), null, null, to - from, null, 0, 0, null);
        }
    }

    private static void append(
            List<ProgressionFact> facts,
            long firstSequence,
            long occurredAt,
            ProgressionFactType type,
            List<UUID> heroIds,
            UUID heroId,
            UUID creatureCombatantId,
            long elapsed,
            CombatAction action,
            int manaSpent,
            int baseExperience,
            CombatBattleSnapshot finalSnapshot) {
        facts.add(new ProgressionFact(
                firstSequence + facts.size(), occurredAt, type, heroIds,
                heroId, creatureCombatantId, elapsed, action, manaSpent,
                baseExperience, finalSnapshot));
    }
}
