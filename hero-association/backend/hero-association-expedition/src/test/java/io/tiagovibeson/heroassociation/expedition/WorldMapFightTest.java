package io.tiagovibeson.heroassociation.expedition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import io.tiagovibeson.heroassociation.contract.WorldContract.*;
import io.tiagovibeson.heroassociation.domain.*;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldMapFightTest {
    @Test void aDungeonWaitsAfterItsBossAndCanRestartFromThePinnedFirstFloor() {
        Plan plan = dungeon();
        var factory = new FightFactory();
        var resolver = new FightResolver();
        var run = run(plan, factory);
        assertEquals("Wolf", run.fight().openingSnapshot().creatures().getFirst().name());
        var first = resolver.resolve(run);
        run = run.finish(first.heroes(), first.outcome());
        assertEquals(RunState.Phase.AWAITING_CONTINUE, run.phase());
        assertEquals(5, run.carried().gold());
        run = run.beginNext(factory.start(run.heroes(), run.world(), 2, Instant.now()));
        assertEquals("Boss", run.fight().openingSnapshot().creatures().getFirst().name());
        assertTrue(run.world().map().encounter(2).boss());
        var second = resolver.resolve(run);
        run = run.finish(second.heroes(), second.outcome());
        assertEquals(RunState.Phase.DUNGEON_COMPLETED, run.phase());
        assertEquals(2, run.world().map().floorNumber(run.encounterIndex()));
        assertEquals(10, run.carried().gold());
        assertThrows(IllegalArgumentException.class, () -> factory.start(first.heroes(), plan, 3, Instant.now()));
        assertEquals(RunState.Phase.SETTLEMENT_PENDING, run.returnBetweenFights().phase());
        var completed = run;
        run = run.beginNext(factory.start(run.heroes(), run.world(), run.nextEncounterIndex(), Instant.now()));
        assertEquals(1, run.encounterIndex());
        assertEquals(1, run.world().map().floorNumber(run.encounterIndex()));
        assertEquals("Wolf", run.fight().openingSnapshot().creatures().getFirst().name());
        assertEquals(completed.world(), run.world()); assertEquals(completed.heroes(), run.heroes());
        assertEquals(completed.carried(), run.carried());
        assertNotEquals(completed.lastOutcome().fightId(), run.fight().fightId());
        var repeated = resolver.resolve(run);
        run = run.finish(repeated.heroes(), repeated.outcome());
        assertEquals(15, run.carried().gold());
        assertEquals(2, run.carried().items().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test void restartReplayProducesTheSameKillsAndLootWithoutChangingThePinnedCatalog() {
        var run = run(dungeon(), new FightFactory());
        var first = new FightResolver().resolve(run);
        var replay = new FightResolver().resolve(run);
        assertEquals(first.outcome(), replay.outcome());
        assertEquals(first.heroes(), replay.heroes());
        assertEquals(CombatStatus.HERO_VICTORY, first.outcome().status());
        assertEquals(1, first.outcome().kills().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, first.outcome().loot().items().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, run.carried().gold());
    }

    @Test void changingLootCannotChangeCombatAndRangedLootReplaysExactly() {
        var original = run(dungeon(), new FightFactory());
        var fight = original.fight();
        var creatures = new TreeMap<String, Creature>();
        fight.creatures().forEach((id, creature) -> creatures.put(id, new Creature(creature.definitionId(), creature.version(),
                creature.name(), creature.baseExperience(), creature.maxHealth(), creature.maxMana(), creature.attackDamage(),
                creature.attackIntervalMilliseconds(), creature.healthRecoveryPerSecond(), creature.manaRecoveryPerSecond(),
                creature.criticalChance(), creature.criticalDamageMultiplier(), new GoldDrop(1, 25, .5),
                List.of(new Drop("ITEM", UuidV7.next(), 1, 5, .05), new Drop("RUNE", UuidV7.next(), 1, 1, .01)))));
        var changedFight = new RunState.FightState(fight.fightId(), fight.startedAt(), fight.randomSeed(), fight.rulesetVersion(),
                fight.rngVersion(), fight.xpRate(), fight.skillRate(), fight.dropRate(), fight.openingSnapshot(), creatures);
        var changedWorld = new Plan(original.world().map(), original.world().creatures().stream()
                .map(creature -> creatures.values().stream().filter(value -> value.definitionId().equals(creature.definitionId()))
                        .findFirst().orElse(creature)).toList());
        var changed = new RunState(original.schemaVersion(), original.stateVersion(), original.expeditionId(), original.ownerManagerId(),
                original.agencyId(), original.partyId(), original.mapId(), original.mapVersion(), original.encounterIndex(), original.phase(),
                original.returnRequested(), original.startedAt(), original.heroes(), original.creature(), original.carried(), changedFight,
                original.lastOutcome(), changedWorld);
        var resolver = new FightResolver();
        var baseline = resolver.plan(original); var ranged = resolver.plan(changed);
        assertEquals(baseline.windows(), ranged.windows()); assertEquals(baseline.heroes(), ranged.heroes());
        assertEquals(baseline.outcome().kills(), ranged.outcome().kills());
        assertEquals(ranged, new FightResolver().plan(changed));
    }

    private RunState run(Plan plan, FightFactory factory) {
        Map<HeroSkill, BigDecimal> points = new EnumMap<>(HeroSkill.class);
        for (HeroSkill skill : HeroSkill.values()) points.put(skill, BigDecimal.ZERO);
        var hero = new RunState.HeroState(UuidV7.next(), "Warrior", HeroClass.WARRIOR, 0, points, 300, 50,
                HeroProgression.MAX_STAMINA_MILLISECONDS, Map.of(), 0, 2);
        Instant now = Instant.now();
        return new RunState(RunState.SCHEMA_VERSION, 1, UuidV7.next(), UuidV7.next(), UuidV7.next(), UuidV7.next(),
                plan.map().definitionId(), plan.map().version(), 1, RunState.Phase.FIGHTING, false, now, List.of(hero),
                WorldPlans.profile(plan.creatures().getFirst()), RunState.CarriedAssets.empty(), factory.start(List.of(hero), plan, 1, now), null, plan);
    }

    private Plan dungeon() {
        UUID item = UuidV7.next();
        Creature wolf = new Creature(UuidV7.next(), 1, "Wolf", 100, 1, 0, 0, 1600, 0, 0, 0, 2, new GoldDrop(5, 5, 1), List.of(new Drop("ITEM", item, 1, 1, 1)));
        Creature boss = new Creature(UuidV7.next(), 1, "Boss", 200, 1, 0, 0, 1600, 0, 0, 0, 2, new GoldDrop(5, 5, 1), List.of());
        var entrance = new Encounter(UuidV7.next(), "Entrance", false, List.of(new Spawn(wolf.definitionId(), 1, 1)));
        var chamber = new Encounter(UuidV7.next(), "Boss chamber", true, List.of(new Spawn(boss.definitionId(), 1, 1)));
        var map = new MapDefinition(UuidV7.next(), 1, "Fixture dungeon", MapKind.DUNGEON,
                List.of(new Floor(1, "Entrance", "LINEAR", List.of(entrance)), new Floor(2, "Chamber", "LINEAR", List.of(chamber))));
        return new Plan(map, List.of(wolf, boss));
    }
}
