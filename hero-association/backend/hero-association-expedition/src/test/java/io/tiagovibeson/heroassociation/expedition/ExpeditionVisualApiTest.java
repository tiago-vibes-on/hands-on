package io.tiagovibeson.heroassociation.expedition;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import io.tiagovibeson.heroassociation.expedition.RedisRunStore.Member;
import io.tiagovibeson.heroassociation.expedition.RunState.CreatureProfile;
import io.tiagovibeson.heroassociation.expedition.RunState.HeroState;
import jakarta.inject.Inject;

@QuarkusTest
class ExpeditionVisualApiTest {

    private static final String KEY = "test-only-expedition-bff-service-key-0123456789";

    @Inject ExpeditionService expeditions;
    @Inject RedisRunStore runs;
    @Inject RedisFightTimelineStore timelines;
    @Inject FightResolver resolver;
    @Inject ExpeditionWorker worker;
    @Inject ObjectMapper mapper;

    @Test
    void visualReplayIsDeterministicAndDoesNotExposePinnedEngineState() throws Exception {
        RunState run = startRun();
        var atOneSecond = run.fight().startedAt().plusSeconds(1);
        FightTimeline plan = timelines.saveIfAbsent(run, resolver.plan(run));
        var first = FightTimelineProjector.at(run, plan, atOneSecond);
        assertEquals(first, FightTimelineProjector.at(run, timelines.get(run), atOneSecond));
        assertEquals(plan, timelines.saveIfAbsent(run, resolver.plan(run)));
        assertEquals(run.fight().fightId(), first.fightId());
        assertEquals(1_000, first.elapsedMilliseconds());
        assertTrue(first.creatures().getFirst().health() < run.creature().maxHealth());
        assertFalse(first.recentEvents().isEmpty());
        assertTrue(first.recentEvents().stream().allMatch(event -> event.sequenceNumber() > 0));
        assertEquals(plan.windows().stream().flatMap(window -> window.events().stream()).count(),
                plan.windows().stream().flatMap(window -> window.events().stream())
                        .map(FightTimeline.Event::sequenceNumber).distinct().count());

        String publicJson = mapper.writeValueAsString(ExpeditionView.from(run, atOneSecond, plan));
        assertTrue(publicJson.contains("\"visual\""));
        assertFalse(publicJson.contains("randomSeed"));
        assertFalse(publicJson.contains("openingSnapshot"));
        assertFalse(publicJson.contains("nextBasicAttackAt"));
    }

    @Test
    void privateVisualReadRequiresServiceKeyAndExactOwner() {
        RunState run = startRun();
        String path = "/internal/v1/expedition-visuals/" + run.ownerManagerId()
                + "/" + run.expeditionId();
        given().when().get(path).then().statusCode(403);
        given().header("X-Hero-Association-Bff-Service-Key", "wrong")
                .when().get(path).then().statusCode(403);
        given().header("X-Hero-Association-Bff-Service-Key", KEY)
                .when().get(path).then().statusCode(200)
                .body("ownerManagerId", org.hamcrest.Matchers.equalTo(run.ownerManagerId().toString()))
                .body("fight.visual.fightId", org.hamcrest.Matchers.equalTo(run.fight().fightId().toString()));
        given().header("X-Hero-Association-Bff-Service-Key", KEY)
                .when().get("/internal/v1/expedition-visuals/" + UuidV7.next()
                        + "/" + run.expeditionId())
                .then().statusCode(404);
    }

    @Test
    void workerKeepsOnePlanForTheRunningFight() {
        RunState run = startRun();
        Member member = new Member(run.ownerManagerId(), run.expeditionId(), run.fight().fightId());
        try {
            runs.schedule(run, run.fight().startedAt());
            worker.tick(run.fight().startedAt());
            FightTimeline saved = timelines.get(run);
            assertNotNull(saved);
            assertEquals(run.fight().fightId(), saved.fightId());
            worker.tick(run.fight().startedAt().plusSeconds(1));
            assertEquals(saved, timelines.get(run));
        } finally {
            runs.unschedule(member);
            timelines.remove(run);
        }
    }

    @Test
    void plannedFramesMatchTheCombatEngineAcrossWindowBoundaries() {
        RunState run = startRun();
        FightTimeline plan = resolver.plan(run);
        for (long seconds : new long[] { 1, 4, 5, 6, 9 }) {
            var at = run.fight().startedAt().plusSeconds(seconds);
            var expected = FightVisualProjector.at(run, at);
            var actual = FightTimelineProjector.at(run, plan, at);
            assertEquals(expected.heroes().stream().map(hero -> hero.health() + ":" + hero.mana()).toList(),
                    actual.heroes().stream().map(hero -> hero.health() + ":" + hero.mana()).toList());
            assertEquals(expected.creatures().stream().map(creature -> creature.health() + ":" + creature.mana()).toList(),
                    actual.creatures().stream().map(creature -> creature.health() + ":" + creature.mana()).toList());
        }
    }

    private RunState startRun() {
        Map<HeroSkill, BigDecimal> points = new EnumMap<>(HeroSkill.class);
        for (HeroSkill skill : HeroSkill.values()) points.put(skill, new BigDecimal("0.000000"));
        HeroState warrior = new HeroState(UuidV7.next(), "Warrior", HeroClass.WARRIOR,
                0, points, 300, 50, Duration.ofHours(48).toMillis(), Map.of(), 0, 2);
        CreatureProfile troll = new CreatureProfile(UuidV7.next(), 1, "Troll", 2_000, 100,
                10, 1_600, 0, 0, 0, 2, 100);
        PreparedEntry entry = new PreparedEntry(UuidV7.next(), UuidV7.next(), UuidV7.next(),
                UuidV7.next(), UuidV7.next(), 1, List.of(warrior), troll);
        RunState run = expeditions.startPrepared(entry, UuidV7.next());
        runs.unschedule(new Member(entry.ownerManagerId(), entry.expeditionId(), run.fight().fightId()));
        return run;
    }
}
