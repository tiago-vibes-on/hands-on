package io.tiagovibeson.heroassociation.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;
import io.tiagovibeson.heroassociation.expedition.RunState.CarriedAssets;
import io.tiagovibeson.heroassociation.expedition.RunState.CreatureProfile;
import io.tiagovibeson.heroassociation.expedition.RunState.HeroState;
import io.tiagovibeson.heroassociation.expedition.RunState.Phase;

class ThreeTrollFightTest {

    @Test
    @Timeout(10)
    void seededThreeHeroPartyProducesAPlayableFightPlan() {
        List<HeroState> heroes = List.of(hero(HeroClass.WARRIOR, 0),
                hero(HeroClass.MAGE, 4058), hero(HeroClass.ARCHER, 0));
        CreatureProfile troll = new CreatureProfile(UuidV7.next(), 1, "Troll",
                2_000, 100, 4, 1_600, 0, 0, 0.1, 2, 100);
        Instant startedAt = Instant.now();
        var fight = new FightFactory().start(heroes, troll, startedAt);
        RunState run = new RunState(RunState.SCHEMA_VERSION, 1, UuidV7.next(),
                UuidV7.next(), UuidV7.next(), UuidV7.next(), UuidV7.next(), 1, 1,
                Phase.FIGHTING, false, startedAt, heroes, troll, CarriedAssets.empty(), fight, null);

        FightTimeline timeline = new FightResolver().plan(run);

        assertEquals(3, fight.openingSnapshot().creatures().size());
        assertEquals(CombatStatus.HERO_VICTORY, timeline.outcome().status());
        assertTrue(timeline.outcome().durationMilliseconds() > 1_000);
        assertTrue(timeline.outcome().durationMilliseconds() < Duration.ofMinutes(5).toMillis());
        assertTrue(timeline.windows().size() > 1);
        assertTrue(timeline.heroes().stream().anyMatch(hero -> hero.experience() > 0));
    }

    private HeroState hero(HeroClass heroClass, long magicPoints) {
        Map<HeroSkill, BigDecimal> points = new EnumMap<>(HeroSkill.class);
        for (HeroSkill skill : HeroSkill.values()) {
            points.put(skill, new BigDecimal("0.000000"));
        }
        points.put(HeroSkill.MAGIC, BigDecimal.valueOf(magicPoints).setScale(6));
        return new HeroState(UuidV7.next(), heroClass.name(), heroClass, 0, points,
                heroClass.getBaseHealth(), heroClass.getBaseMana(),
                Duration.ofHours(48).toMillis(), Map.of(), 0, 2);
    }
}
