package io.tiagovibeson.heroassociation.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import io.tiagovibeson.heroassociation.expedition.ExpeditionService.RunConflictException;
import io.tiagovibeson.heroassociation.expedition.ExpeditionService.RunNotFoundException;
import io.tiagovibeson.heroassociation.expedition.RedisRunStore.Member;
import io.tiagovibeson.heroassociation.expedition.RunState.CreatureProfile;
import io.tiagovibeson.heroassociation.expedition.RunState.HeroState;
import io.tiagovibeson.heroassociation.expedition.RunState.Phase;
import jakarta.inject.Inject;

@QuarkusTest
class ExpeditionFlowTest {

    @Inject
    ExpeditionService service;

    @Inject
    ExpeditionWorker worker;

    @Inject
    RedisRunStore store;

    @Test
    void runsWithoutViewerAndWaitsForExplicitContinue() {
        PreparedEntry entry = entry(hero(HeroClass.WARRIOR, 300), troll(0));
        UUID startCommand = UuidV7.next();
        RunState started = service.startPrepared(entry, startCommand);
        assertEquals(Phase.FIGHTING, started.phase());
        assertEquals(3, started.fight().openingSnapshot().creatures().size());
        assertEquals(3, started.fight().openingSnapshot().creatures().stream()
                .map(creature -> creature.id()).distinct().count());
        store.unschedule(new Member(entry.ownerManagerId(), entry.expeditionId(), started.fight().fightId()));
        assertEquals(started, service.startPrepared(entry, startCommand));
        assertThrows(RunConflictException.class,
                () -> service.startPrepared(entryWithSameManager(entry), UuidV7.next()));
        assertThrows(RunNotFoundException.class,
                () -> service.get(UuidV7.next(), started.expeditionId()));

        assertEquals(1, worker.tick(started.fight().startedAt().plus(Duration.ofMinutes(10))));
        RunState won = service.get(entry.ownerManagerId(), entry.expeditionId());
        assertEquals(Phase.AWAITING_CONTINUE, won.phase());
        assertEquals(2, won.stateVersion());
        assertEquals(1, won.encounterIndex());
        assertEquals(450, won.heroes().getFirst().experience());
        assertTrue(won.heroes().getFirst().staminaMilliseconds() < entry.heroes().getFirst().staminaMilliseconds());
        assertEquals(0, worker.tick(started.fight().startedAt().plus(Duration.ofMinutes(20))));
        assertEquals(won, service.get(entry.ownerManagerId(), entry.expeditionId()));

        UUID continueCommand = UuidV7.next();
        RunState next = service.continueRun(entry.ownerManagerId(), entry.expeditionId(), continueCommand, won.stateVersion());
        assertEquals(Phase.FIGHTING, next.phase());
        assertEquals(2, next.encounterIndex());
        assertNotEquals(started.fight().fightId(), next.fight().fightId());
        assertEquals(won.heroes(), next.heroes());
        store.unschedule(new Member(entry.ownerManagerId(), entry.expeditionId(), next.fight().fightId()));
        assertEquals(next, service.continueRun(entry.ownerManagerId(), entry.expeditionId(),
                continueCommand, won.stateVersion()));
        assertThrows(RunConflictException.class, () -> service.returnRun(
                entry.ownerManagerId(), entry.expeditionId(), continueCommand, won.stateVersion()));
        assertEquals(1, worker.tick(next.fight().startedAt().plus(Duration.ofMinutes(10))));
        assertEquals(Phase.AWAITING_CONTINUE, service.get(entry.ownerManagerId(), entry.expeditionId()).phase());
    }

    @Test
    void aConflictingActiveRunReleasesOnlyTheNewReservation() {
        PreparedEntry live = entry(hero(HeroClass.WARRIOR, 300), troll(0));
        RunState liveRun = service.startPrepared(live, UuidV7.next());
        store.unschedule(new Member(live.ownerManagerId(), live.expeditionId(), liveRun.fight().fightId()));
        PreparedEntry blocked = entryWithSameManager(live);
        AtomicBoolean released = new AtomicBoolean();
        CoreAdmissionClient core = new CoreAdmissionClient(new ObjectMapper()) {
            @Override
            public PreparedEntry reserve(UUID expeditionId, UUID agencyId, UUID partyId,
                                         UUID mapId, String token) {
                return blocked;
            }

            @Override
            public boolean releaseProvenAbsent(UUID expeditionId, UUID managerId) {
                released.set(true);
                assertEquals(blocked.expeditionId(), expeditionId);
                assertEquals(blocked.ownerManagerId(), managerId);
                return true;
            }
        };
        ExpeditionEntryService entryService = new ExpeditionEntryService(core, service, store);
        assertThrows(RunConflictException.class, () -> entryService.start(blocked.expeditionId(),
                blocked.agencyId(), blocked.partyId(), blocked.mapId(), UuidV7.next(), "player-token"));
        assertTrue(released.get());
        assertThrows(RunConflictException.class, () -> service.startPrepared(blocked, UuidV7.next()));
        assertEquals(live.expeditionId(), service.get(live.ownerManagerId(), live.expeditionId()).expeditionId());
    }

    @Test
    void orphanCancellationFencesEveryDelayedStartForThatRunId() {
        PreparedEntry entry = entry(hero(HeroClass.WARRIOR, 300), troll(0));
        assertTrue(store.cancelIfAbsent(entry.ownerManagerId(), entry.expeditionId()));
        assertTrue(store.cancelIfAbsent(entry.ownerManagerId(), entry.expeditionId()));
        assertThrows(RunConflictException.class, () -> service.startPrepared(entry, UuidV7.next()));
        assertEquals(null, store.get(entry.ownerManagerId(), entry.expeditionId()));

        PreparedEntry live = entry(hero(HeroClass.WARRIOR, 300), troll(0));
        RunState liveRun = service.startPrepared(live, UuidV7.next());
        store.unschedule(new Member(live.ownerManagerId(), live.expeditionId(), liveRun.fight().fightId()));
        assertFalse(store.cancelIfAbsent(live.ownerManagerId(), live.expeditionId()));
    }

    @Test
    void returnDuringFightWaitsForTheTerminalBoundary() {
        PreparedEntry entry = entry(hero(HeroClass.WARRIOR, 300), troll(0));
        RunState started = service.startPrepared(entry, UuidV7.next());
        UUID returnCommand = UuidV7.next();
        RunState returning = service.returnRun(entry.ownerManagerId(), entry.expeditionId(), returnCommand, 1);
        assertEquals(Phase.FIGHTING, returning.phase());
        assertTrue(returning.returnRequested());
        assertEquals(returning, service.returnRun(entry.ownerManagerId(), entry.expeditionId(), returnCommand, 1));
        assertEquals(1, worker.tick(started.fight().startedAt().plus(Duration.ofMinutes(10))));

        RunState pending = service.get(entry.ownerManagerId(), entry.expeditionId());
        assertEquals(Phase.SETTLEMENT_PENDING, pending.phase());
        assertFalse(pending.returnRequested());
        assertEquals(3, pending.stateVersion());
        assertThrows(RunConflictException.class, () -> service.continueRun(
                entry.ownerManagerId(), entry.expeditionId(), UuidV7.next(), pending.stateVersion()));
    }

    @Test
    void wipeParksThePartyUntilExplicitReturn() {
        PreparedEntry entry = entry(hero(HeroClass.WARRIOR, 1), troll(500));
        RunState started = service.startPrepared(entry, UuidV7.next());
        assertEquals(1, worker.tick(started.fight().startedAt().plus(Duration.ofMinutes(1))));
        RunState wiped = service.get(entry.ownerManagerId(), entry.expeditionId());
        assertEquals(Phase.WIPED, wiped.phase());
        assertEquals(0, wiped.heroes().getFirst().health());
        assertThrows(RunConflictException.class, () -> service.continueRun(
                entry.ownerManagerId(), entry.expeditionId(), UuidV7.next(), wiped.stateVersion()));
        assertThrows(RunConflictException.class, () -> service.returnRun(
                entry.ownerManagerId(), entry.expeditionId(), UuidV7.next(), 1));
        RunState pending = service.returnRun(
                entry.ownerManagerId(), entry.expeditionId(), UuidV7.next(), wiped.stateVersion());
        assertEquals(Phase.SETTLEMENT_PENDING, pending.phase());
    }

    @Test
    void twoWorkersAndRebuiltScheduleCommitOnlyOneOutcome() throws Exception {
        PreparedEntry entry = entry(hero(HeroClass.WARRIOR, 300), troll(0));
        RunState started = service.startPrepared(entry, UuidV7.next());
        store.unschedule(new Member(entry.ownerManagerId(), entry.expeditionId(), started.fight().fightId()));
        assertTrue(worker.rebuildDueIndex() >= 1);
        var future = started.fight().startedAt().plus(Duration.ofMinutes(10));
        CountDownLatch gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { gate.await(); return worker.tick(future); });
            var second = executor.submit(() -> { gate.await(); return worker.tick(future); });
            gate.countDown();
            assertTrue(first.get() + second.get() >= 1);
        }
        RunState won = service.get(entry.ownerManagerId(), entry.expeditionId());
        assertEquals(2, won.stateVersion());
        assertEquals(1, won.encounterIndex());
        assertEquals(0, worker.tick(future.plusSeconds(1)));
    }

    @Test
    void pinnedExperienceRateUsesAdditiveHighStaminaAndMultiplicativeLowStamina() {
        assertEquals(150, FightResolver.experienceAward(100, Duration.ofHours(48).toMillis(), BigDecimal.ONE));
        assertEquals(250, FightResolver.experienceAward(100, Duration.ofHours(48).toMillis(), new BigDecimal("2")));
        assertEquals(100, FightResolver.experienceAward(100, Duration.ofHours(14).toMillis(), new BigDecimal("2")));
        assertEquals(50, FightResolver.experienceAward(100, Duration.ofHours(14).toMillis(), BigDecimal.ONE));
    }

    private PreparedEntry entry(HeroState hero, CreatureProfile creature) {
        return new PreparedEntry(UuidV7.next(), UuidV7.next(), UuidV7.next(), UuidV7.next(),
                UuidV7.next(), 1, java.util.List.of(hero), creature);
    }

    private PreparedEntry entryWithSameManager(PreparedEntry entry) {
        return new PreparedEntry(UuidV7.next(), entry.ownerManagerId(), entry.agencyId(), entry.partyId(),
                entry.mapId(), entry.mapVersion(), entry.heroes(), entry.creature());
    }

    private HeroState hero(HeroClass heroClass, int health) {
        Map<HeroSkill, BigDecimal> points = new EnumMap<>(HeroSkill.class);
        for (HeroSkill skill : HeroSkill.values()) {
            points.put(skill, new BigDecimal("0.000000"));
        }
        return new HeroState(UuidV7.next(), "Test Hero", heroClass, 0, points, health,
                heroClass.getBaseMana(), Duration.ofHours(48).toMillis(), Map.of(), 0, 2);
    }

    private CreatureProfile troll(int damage) {
        return new CreatureProfile(UuidV7.next(), 1, "Troll", 2_000, 100, damage, 1_600,
                0, 0, 0, 2, 100);
    }
}
