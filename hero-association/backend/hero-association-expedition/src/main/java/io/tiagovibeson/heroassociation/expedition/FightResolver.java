package io.tiagovibeson.heroassociation.expedition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroProgression;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.combat.CombatAction;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattle;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattleSnapshot;
import io.tiagovibeson.heroassociation.domain.combat.CombatEvent;
import io.tiagovibeson.heroassociation.domain.combat.CombatHit;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;
import io.tiagovibeson.heroassociation.domain.combat.CombatantSnapshot;
import io.tiagovibeson.heroassociation.expedition.RunState.FightState;
import io.tiagovibeson.heroassociation.expedition.RunState.HeroState;
import io.tiagovibeson.heroassociation.expedition.RunState.Outcome;
import jakarta.enterprise.context.ApplicationScoped;

/** Replays one pinned fight; no database, broker, viewer, or durable hit writes. */
@ApplicationScoped
public class FightResolver {

    private static final long STEP_MILLISECONDS = 1_000;
    private static final long WINDOW_MILLISECONDS = 5_000;
    private static final long MAX_FIGHT_MILLISECONDS = 30 * 60 * 1_000L;
    private static final int MAX_EVENTS = 100_000;
    private static final BigDecimal MAGIC_POINTS_PER_MANA = new BigDecimal("0.05");

    public Resolution resolve(RunState run) {
        FightTimeline timeline = plan(run);
        return new Resolution(timeline.outcome(), timeline.heroes());
    }

    /** Calculate the current non-interactive fight once; viewers receive slices of this plan. */
    public FightTimeline plan(RunState run) {
        FightState fight = run.fight();
        if (run.phase() != RunState.Phase.FIGHTING || fight == null || !"core-v1".equals(fight.rulesetVersion())
                || !"java-random-v1".equals(fight.rngVersion())) {
            throw new IllegalArgumentException("Only a supported fighting run can be resolved.");
        }
        CombatBattle battle = CombatBattle.restore(fight.openingSnapshot());
        Random random = new Random(fight.randomSeed());
        Map<String, MutableHero> heroes = new HashMap<>();
        for (HeroState hero : run.heroes()) {
            heroes.put(hero.heroId().toString(), new MutableHero(hero));
        }
        Set<String> creatureIds = fight.openingSnapshot().creatures().stream()
                .map(CombatantSnapshot::id).collect(java.util.stream.Collectors.toSet());
        Set<String> defeated = new java.util.LinkedHashSet<>();
        long processedAt = 0;
        int eventCount = 0;
        long terminalAt = -1;
        var windows = new ArrayList<FightTimeline.Window>();
        var windowEvents = new ArrayList<FightTimeline.Event>();
        CombatBattleSnapshot windowOpening = battle.snapshot();

        for (long target = STEP_MILLISECONDS; target <= MAX_FIGHT_MILLISECONDS; target += STEP_MILLISECONDS) {
            List<CombatEvent> events = battle.advanceTo(target, random::nextDouble);
            eventCount = Math.addExact(eventCount, events.size());
            if (eventCount > MAX_EVENTS) {
                throw new IllegalStateException("Fight exceeded the bounded event budget.");
            }
            long sequence = eventCount - events.size();
            for (CombatEvent event : events) {
                windowEvents.add(new FightTimeline.Event(++sequence, event));
            }
            boolean terminal = battle.getStatus() != CombatStatus.IN_PROGRESS;
            long activeUntil = terminal ? events.getLast().occurredAtMilliseconds() : target;
            for (CombatEvent event : events) {
                consumeLivingStamina(heroes, event.occurredAtMilliseconds() - processedAt);
                processedAt = event.occurredAtMilliseconds();
                awardAction(event, heroes, creatureIds, fight.skillRate());
                for (CombatHit hit : event.hits()) {
                    if (!hit.defeated()) {
                        continue;
                    }
                    if (creatureIds.contains(hit.targetId()) && defeated.add(hit.targetId())) {
                        var definition = fight.creatures().get(hit.targetId());
                        int baseExperience = definition == null ? run.creature().baseExperience() : definition.baseExperience();
                        for (MutableHero hero : heroes.values()) {
                            if (hero.alive) {
                                hero.experience = Math.addExact(hero.experience,
                                        experienceAward(baseExperience, hero.stamina, fight.xpRate()));
                            }
                        }
                    } else {
                        MutableHero fallen = heroes.get(hit.targetId());
                        if (fallen != null) {
                            fallen.alive = false;
                        }
                    }
                }
            }
            consumeLivingStamina(heroes, activeUntil - processedAt);
            processedAt = activeUntil;
            if (target % WINDOW_MILLISECONDS == 0 || terminal) {
                windows.add(new FightTimeline.Window(windowOpening.currentTimeMilliseconds(),
                        target, windowOpening, windowEvents));
                windowOpening = battle.snapshot();
                windowEvents = new ArrayList<>();
            }
            if (terminal) {
                terminalAt = activeUntil;
                break;
            }
        }
        if (terminalAt < 0) {
            throw new IllegalStateException("Fight did not resolve within thirty minutes; leave the run unchanged.");
        }

        Map<String, CombatantSnapshot> snapshots = new HashMap<>();
        for (CombatantSnapshot combatant : battle.snapshot().heroes()) {
            snapshots.put(combatant.id(), combatant);
        }
        List<HeroState> updated = new ArrayList<>(run.heroes().size());
        for (HeroState previous : run.heroes()) {
            MutableHero progress = heroes.get(previous.heroId().toString());
            CombatantSnapshot resources = snapshots.get(previous.heroId().toString());
            updated.add(new HeroState(previous.heroId(), previous.name(), previous.heroClass(),
                    progress.experience, Map.copyOf(progress.points), resources.currentHealth(),
                    resources.currentMana(), progress.stamina, previous.runeIds(),
                    previous.criticalChance(), previous.criticalDamageMultiplier()));
        }
        Map<java.util.UUID, Integer> kills = new java.util.TreeMap<>();
        long gold = 0;
        Map<java.util.UUID, Integer> items = new java.util.TreeMap<>();
        Map<java.util.UUID, Integer> runes = new java.util.TreeMap<>();
        Random dropRandom = new Random(fight.randomSeed() ^ 0x776f726c642d7631L);
        for (String id : defeated) {
            var definition = fight.creatures().get(id);
            if (definition == null) {
                kills.merge(run.creature().definitionId(), 1, Math::addExact);
                continue;
            }
            kills.merge(definition.definitionId(), 1, Math::addExact);
            gold = Math.addExact(gold, BigDecimal.valueOf(definition.goldDrop()).multiply(fight.dropRate()).setScale(0, RoundingMode.DOWN).longValueExact());
            for (var drop : definition.drops()) {
                double chance = Math.min(1, drop.chance() * fight.dropRate().doubleValue());
                if (dropRandom.nextDouble() < chance) ("ITEM".equals(drop.resourceType()) ? items : runes).merge(drop.resourceId(), drop.quantity(), Math::addExact);
            }
        }
        return new FightTimeline(fight.fightId(),
                new Outcome(fight.fightId(), battle.getStatus(), terminalAt, kills, new RunState.CarriedAssets(gold, items, runes)), List.copyOf(updated), windows);
    }

    static long experienceAward(int baseExperience, long stamina, BigDecimal xpRate) {
        BigDecimal effective = stamina > HeroProgression.HIGH_STAMINA_MILLISECONDS
                ? xpRate.add(new BigDecimal("0.5"))
                : stamina < HeroProgression.LOW_STAMINA_MILLISECONDS
                    ? xpRate.multiply(new BigDecimal("0.5")) : xpRate;
        return BigDecimal.valueOf(baseExperience).multiply(effective)
                .setScale(0, RoundingMode.DOWN).longValueExact();
    }

    private void awardAction(CombatEvent event, Map<String, MutableHero> heroes, Set<String> creatureIds,
                             BigDecimal skillRate) {
        MutableHero hero = heroes.get(event.actorId());
        if (hero == null) {
            return;
        }
        if (event.action() == CombatAction.BASIC_ATTACK
                && event.hits().stream().anyMatch(hit -> creatureIds.contains(hit.targetId()))) {
            if (hero.heroClass == HeroClass.WARRIOR) {
                hero.award(HeroSkill.MELEE, BigDecimal.ONE, skillRate);
            } else if (hero.heroClass == HeroClass.ARCHER) {
                hero.award(HeroSkill.DISTANCE, BigDecimal.ONE, skillRate);
            }
        }
        if (event.manaSpent() > 0) {
            hero.award(HeroSkill.MAGIC,
                    BigDecimal.valueOf(event.manaSpent()).multiply(MAGIC_POINTS_PER_MANA), skillRate);
        }
    }

    private void consumeLivingStamina(Map<String, MutableHero> heroes, long milliseconds) {
        if (milliseconds <= 0) {
            return;
        }
        for (MutableHero hero : heroes.values()) {
            if (hero.alive) {
                hero.stamina = Math.max(0, hero.stamina - milliseconds);
            }
        }
    }

    public record Resolution(Outcome outcome, List<HeroState> heroes) {
        public Resolution {
            heroes = List.copyOf(heroes);
        }
    }

    private static final class MutableHero {
        private final HeroClass heroClass;
        private final Map<HeroSkill, BigDecimal> points;
        private long experience;
        private long stamina;
        private boolean alive;

        private MutableHero(HeroState state) {
            heroClass = state.heroClass();
            points = new EnumMap<>(state.skillPoints());
            experience = state.experience();
            stamina = state.staminaMilliseconds();
            alive = state.health() > 0;
        }

        private void award(HeroSkill skill, BigDecimal basePoints, BigDecimal skillRate) {
            BigDecimal award = HeroProgression.combatSkillAward(heroClass, skill, basePoints, stamina)
                    .multiply(skillRate);
            points.put(skill, points.get(skill).add(award).setScale(6, RoundingMode.DOWN));
        }
    }
}
