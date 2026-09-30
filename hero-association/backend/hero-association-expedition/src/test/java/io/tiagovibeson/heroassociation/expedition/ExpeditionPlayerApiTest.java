package io.tiagovibeson.heroassociation.expedition;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import io.tiagovibeson.heroassociation.expedition.ExpeditionService.RunNotFoundException;
import io.tiagovibeson.heroassociation.expedition.RedisRunStore.Member;
import io.tiagovibeson.heroassociation.expedition.RunState.CreatureProfile;
import io.tiagovibeson.heroassociation.expedition.RunState.HeroState;
import jakarta.inject.Inject;

@QuarkusTest
class ExpeditionPlayerApiTest {

    @Inject ExpeditionService expeditions;
    @Inject ExpeditionEntryService entries;
    @Inject RedisRunStore runs;
    @Inject ObjectMapper mapper;

    @Test
    void unauthenticatedPlayerApiIsRejected() {
        given().when().get("/api/v1/expeditions/active").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "test-player")
    void disabledPlayerApiDoesNotExposeActiveRuns() {
        given().when().get("/api/v1/expeditions/active").then().statusCode(404);
    }

    @Test
    void anAuthenticatedManagerOnlyReadsTheirOwnRunAndProjectionHidesTheSeed() throws Exception {
        UUID managerId = UuidV7.next();
        UUID foreignManagerId = UuidV7.next();
        Map<HeroSkill, BigDecimal> points = new EnumMap<>(HeroSkill.class);
        for (HeroSkill skill : HeroSkill.values()) points.put(skill, new BigDecimal("0.000000"));
        HeroState hero = new HeroState(UuidV7.next(), "Owner Warrior", HeroClass.WARRIOR,
                0, points, 300, 50, Duration.ofHours(48).toMillis(), Map.of(), 0, 2);
        CreatureProfile troll = new CreatureProfile(UuidV7.next(), 1, "Troll", 2_000, 100,
                10, 1_600, 0, 0, 0, 2, 100);
        PreparedEntry entry = new PreparedEntry(UuidV7.next(), managerId, UuidV7.next(),
                UuidV7.next(), UuidV7.next(), 1, List.of(hero), troll);
        RunState started = expeditions.startPrepared(entry, UuidV7.next());
        runs.unschedule(new Member(managerId, entry.expeditionId(), started.fight().fightId()));

        CoreAdmissionClient trustedIdentity = new CoreAdmissionClient(mapper) {
            @Override
            public UUID currentManagerId(String token) {
                return "owner".equals(token) ? managerId : foreignManagerId;
            }
        };
        ExpeditionPlayerService players = new ExpeditionPlayerService(trustedIdentity, entries, expeditions);
        assertEquals(started, players.get(entry.expeditionId(), "owner"));
        assertEquals(started, players.active("owner"));
        assertEquals(null, players.active("foreign"));
        assertThrows(RunNotFoundException.class, () -> players.get(entry.expeditionId(), "foreign"));
        assertThrows(RunNotFoundException.class, () -> players.continueRun(
                entry.expeditionId(), UuidV7.next(), started.stateVersion(), "foreign"));
        assertThrows(RunNotFoundException.class, () -> players.returnRun(
                entry.expeditionId(), UuidV7.next(), started.stateVersion(), "foreign"));

        String json = mapper.writeValueAsString(ExpeditionView.from(started));
        assertFalse(json.contains("randomSeed"));
        assertFalse(json.contains("openingSnapshot"));
    }
}
