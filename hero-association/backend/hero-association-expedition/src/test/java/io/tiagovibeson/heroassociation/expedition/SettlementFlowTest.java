package io.tiagovibeson.heroassociation.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import io.tiagovibeson.heroassociation.expedition.RunState.CreatureProfile;
import io.tiagovibeson.heroassociation.expedition.RunState.HeroState;
import io.tiagovibeson.heroassociation.expedition.RunState.Phase;
import jakarta.inject.Inject;

@QuarkusTest
class SettlementFlowTest {

    @Inject ExpeditionService service;
    @Inject ExpeditionWorker fights;
    @Inject RedisRunStore runs;
    @Inject RedisSettlementStore settlements;
    @Inject SettlementCodec codec;
    @Inject SettlementAckHandler acknowledgments;
    @Inject ObjectMapper mapper;

    @Test
    void retainsFrozenProgressUntilMatchingOwnerAcknowledgment() throws Exception {
        Map<HeroSkill, BigDecimal> points = new EnumMap<>(HeroSkill.class);
        for (HeroSkill skill : HeroSkill.values()) points.put(skill, new BigDecimal("0.000000"));
        HeroState hero = new HeroState(UuidV7.next(), "Settlement Warrior", HeroClass.WARRIOR,
                0, points, 300, 50, Duration.ofHours(48).toMillis(), Map.of(), 0, 2);
        CreatureProfile troll = new CreatureProfile(UuidV7.next(), 1, "Troll", 2_000, 100,
                10, 1_600, 0, 0, 0, 2, 100);
        PreparedEntry entry = new PreparedEntry(UuidV7.next(), UuidV7.next(), UuidV7.next(),
                UuidV7.next(), UuidV7.next(), 1, List.of(hero), troll);
        RunState started = service.startPrepared(entry, UuidV7.next());
        assertEquals(1, fights.tick(started.fight().startedAt().plus(Duration.ofMinutes(10))));
        RunState won = service.get(entry.ownerManagerId(), entry.expeditionId());
        RunState pending = service.returnRun(entry.ownerManagerId(), entry.expeditionId(),
                UuidV7.next(), won.stateVersion());
        assertEquals(Phase.SETTLEMENT_PENDING, pending.phase());
        assertTrue(settlements.due(Instant.now().plusSeconds(2), 64).stream()
                .anyMatch(member -> member.expeditionId().equals(entry.expeditionId())));
        assertTrue(pending.heroes().getFirst().experience() >= 100);
        RedisSettlementStore.Member member = new RedisSettlementStore.Member(
                entry.ownerManagerId(), entry.expeditionId());
        settlements.unschedule(member);
        assertTrue(settlements.rebuildDueIndex() >= 1);
        assertTrue(settlements.due(Instant.now().plusSeconds(2), 64).contains(member));

        String digest = codec.digest(codec.encode(SettlementEnvelope.from(pending)));
        byte[] ack = ack(entry, digest);
        assertThrows(IllegalStateException.class,
                () -> acknowledgments.accept(ack, entry.expeditionId().toString(), "application/json"));
        assertEquals(pending, service.get(entry.ownerManagerId(), entry.expeditionId()));
        SettlementWorker failedPublisher = new SettlementWorker(runs, settlements, codec,
                (body, id) -> { throw new IllegalStateException("Rabbit unavailable"); });
        assertEquals(0, failedPublisher.tick(Instant.now().plusSeconds(2)));
        assertEquals(pending, service.get(entry.ownerManagerId(), entry.expeditionId()));
        assertNull(settlements.publishedDigest(entry.ownerManagerId()));
        SettlementWorker recoveredPublisher = new SettlementWorker(runs, settlements, codec,
                (body, id) -> { });
        assertTrue(recoveredPublisher.tick(Instant.now().plusSeconds(20)) >= 1);
        assertEquals(digest, settlements.publishedDigest(entry.ownerManagerId()));
        assertThrows(IllegalArgumentException.class,
                () -> acknowledgments.accept(ack(entry, "0".repeat(64)),
                        entry.expeditionId().toString(), "application/json"));
        assertEquals(pending, service.get(entry.ownerManagerId(), entry.expeditionId()));

        assertTrue(acknowledgments.accept(ack, entry.expeditionId().toString(), "application/json"));
        assertNull(runs.get(entry.ownerManagerId(), entry.expeditionId()));
        assertEquals(digest, settlements.closedDigest(entry.ownerManagerId(), entry.expeditionId()));
        assertFalse(acknowledgments.accept(ack, entry.expeditionId().toString(), "application/json"));
    }

    private byte[] ack(PreparedEntry entry, String digest) throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("schemaVersion", 1);
        node.put("expeditionId", entry.expeditionId().toString());
        node.put("ownerManagerId", entry.ownerManagerId().toString());
        node.put("digest", digest);
        return mapper.writeValueAsBytes(node);
    }
}
