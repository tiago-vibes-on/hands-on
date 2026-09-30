package io.tiagovibeson.heroassociation.combat.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tiagovibeson.heroassociation.combat.api.AdvanceBattleRequest;
import io.tiagovibeson.heroassociation.combat.api.AdvanceBattleResponse;
import io.tiagovibeson.heroassociation.combat.api.StartBattleRequest;
import io.tiagovibeson.heroassociation.combat.domain.BattleEventRecord;
import io.tiagovibeson.heroassociation.combat.domain.BattleProgressionBatchRecord;
import io.tiagovibeson.heroassociation.combat.domain.BattleRecord;
import io.tiagovibeson.heroassociation.combat.domain.ProgressionFact;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattle;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattleSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;

@ApplicationScoped
public class BattleAdvanceService {

    private static final long MAX_ADVANCE_MILLISECONDS = 10_000;

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public BattleAdvanceService(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AdvanceBattleResponse advance(UUID battleId, AdvanceBattleRequest request) {
        if (battleId == null || battleId.version() != 7 || request == null || request.targetTimeMilliseconds() <= 0) {
            throw new BadRequestException("Advancement needs a UUIDv7 battle ID and positive target time.");
        }

        BattleRecord record = entityManager.find(BattleRecord.class, battleId, LockModeType.PESSIMISTIC_WRITE);
        if (record == null) {
            throw new NotFoundException("Battle was not found.");
        }
        CombatBattleSnapshot before = read(record.getSnapshotJson(), CombatBattleSnapshot.class);
        long target = request.targetTimeMilliseconds();
        if (target == before.currentTimeMilliseconds()) {
            return response(record, before, true);
        }
        if (target < before.currentTimeMilliseconds()) {
            throw new WebApplicationException("Battle time cannot move backwards.", 409);
        }
        if (!"PREPARED".equals(record.getStatus()) && !"IN_PROGRESS".equals(record.getStatus())) {
            throw new WebApplicationException("Battle is already complete.", 409);
        }
        if (target - before.currentTimeMilliseconds() > MAX_ADVANCE_MILLISECONDS) {
            throw new BadRequestException("Advance at most 10 seconds per request.");
        }

        return advanceLocked(record, before, target, Instant.now());
    }

    @Transactional
    public boolean advanceDue(UUID battleId, Instant now) {
        BattleRecord record = entityManager.find(BattleRecord.class, battleId, LockModeType.PESSIMISTIC_WRITE);
        if (record == null || (!"PREPARED".equals(record.getStatus())
                && !"IN_PROGRESS".equals(record.getStatus()))) {
            return false;
        }
        long elapsed = Duration.between(record.getLastAdvancedAt(), now).toMillis();
        if (elapsed <= 0) {
            return false;
        }
        CombatBattleSnapshot before = read(record.getSnapshotJson(), CombatBattleSnapshot.class);
        long step = Math.min(elapsed, MAX_ADVANCE_MILLISECONDS);
        long target = Math.addExact(before.currentTimeMilliseconds(), step);
        advanceLocked(record, before, target, record.getLastAdvancedAt().plusMillis(step));
        return true;
    }

    private AdvanceBattleResponse advanceLocked(
            BattleRecord record, CombatBattleSnapshot before, long target, Instant advancedAt) {
        StartBattleRequest start = read(record.getRequestJson(), StartBattleRequest.class);
        CombatBattle battle = CombatBattle.restore(before);
        var events = battle.advanceTo(target, ThreadLocalRandom.current()::nextDouble);
        CombatBattleSnapshot after = battle.snapshot();
        List<ProgressionFact> facts = BattleProgressionProjector.project(
                start, before, events, after, record.getNextFactSequence());
        Instant now = Instant.now();

        long eventSequence = record.getNextEventSequence();
        for (var event : events) {
            entityManager.persist(new BattleEventRecord(record, eventSequence++, write(event), now));
        }
        long factSequence = record.getNextFactSequence() + facts.size();
        if (!facts.isEmpty()) {
            entityManager.persist(new BattleProgressionBatchRecord(
                    record, record.getNextFactSequence(), factSequence - 1, write(facts), now));
        }
        record.advance(write(after), after.status().name(), eventSequence, factSequence, advancedAt);
        return response(record, after, false);
    }

    private AdvanceBattleResponse response(BattleRecord record, CombatBattleSnapshot snapshot, boolean replayed) {
        return new AdvanceBattleResponse(
                record.getId(), record.getStatus(), snapshot.currentTimeMilliseconds(),
                record.getNextEventSequence() - 1, record.getNextFactSequence() - 1, replayed);
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored battle state is invalid.", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Battle event could not be serialized.", exception);
        }
    }
}
