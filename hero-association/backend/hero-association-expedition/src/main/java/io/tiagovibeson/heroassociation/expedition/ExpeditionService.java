package io.tiagovibeson.heroassociation.expedition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.tiagovibeson.heroassociation.expedition.RedisRunStore.Result;
import io.tiagovibeson.heroassociation.expedition.RunState.Phase;
import jakarta.enterprise.context.ApplicationScoped;

/** Internal run commands. No browser or unreserved start endpoint calls these yet. */
@ApplicationScoped
public class ExpeditionService {

    private final RedisRunStore store;
    private final RedisSettlementStore settlements;
    private final FightFactory fightFactory;
    private final ObjectMapper digestMapper;

    public ExpeditionService(RedisRunStore store, RedisSettlementStore settlements,
                             FightFactory fightFactory, ObjectMapper mapper) {
        this.store = store;
        this.settlements = settlements;
        this.fightFactory = fightFactory;
        this.digestMapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    /** The caller must first have reserved this exact Expedition ID in Core. */
    public RunState startPrepared(PreparedEntry entry, UUID commandId) {
        requireCommandId(commandId);
        String digest = digest("START", entry);
        Instant now = Instant.now();
        RunState run = new RunState(RunState.SCHEMA_VERSION, 1, entry.expeditionId(),
                entry.ownerManagerId(), entry.agencyId(), entry.partyId(), entry.mapId(),
                entry.mapVersion(), 1, Phase.FIGHTING, false, now, entry.heroes(),
                entry.creature(), RunState.CarriedAssets.empty(),
                fightFactory.start(entry.heroes(), entry.world(), 1, now), null, entry.world(),
                entry.quest() == null || entry.quest().assignment() == null ? null
                        : new io.tiagovibeson.heroassociation.contract.QuestContract.Progress(entry.quest(), entry.quest().assignment().progress()));
        Result result = store.start(run, commandId, digest);
        if (result == Result.CREATED) {
            store.schedule(run, now);
            return run;
        }
        if (result == Result.DUPLICATE) {
            RunState existing = requireRun(entry.ownerManagerId(), entry.expeditionId());
            repairSchedule(existing);
            return existing;
        }
        throw new RunConflictException("Cannot start Expedition: " + result);
    }

    public RunState active(UUID managerId) {
        return store.active(managerId);
    }

    public RunState get(UUID managerId, UUID expeditionId) {
        return requireRun(managerId, expeditionId);
    }

    public RunState continueRun(UUID managerId, UUID expeditionId, UUID commandId, long expectedVersion) {
        requireCommandId(commandId);
        String digest = digest("CONTINUE", expeditionId + ":" + expectedVersion);
        RunState duplicate = priorResult(managerId, expeditionId, commandId, digest);
        if (duplicate != null) {
            repairSchedule(duplicate);
            return duplicate;
        }
        RunState current = requireRun(managerId, expeditionId);
        if (current.stateVersion() != expectedVersion || !current.canContinue()) {
            throw new RunConflictException("Expedition cannot continue from this version or phase.");
        }
        RunState updated = current.beginNext(fightFactory.start(current.heroes(), current.world(), current.nextEncounterIndex(), Instant.now()));
        Result result = store.command(current, updated, commandId, digest);
        if (result == Result.UPDATED) {
            store.schedule(updated, updated.fight().startedAt());
            return updated;
        }
        if (result == Result.DUPLICATE) {
            RunState existing = requireRun(managerId, expeditionId);
            repairSchedule(existing);
            return existing;
        }
        throw new RunConflictException("Cannot continue Expedition: " + result);
    }

    public RunState returnRun(UUID managerId, UUID expeditionId, UUID commandId, long expectedVersion) {
        requireCommandId(commandId);
        String digest = digest("RETURN", expeditionId + ":" + expectedVersion);
        RunState duplicate = priorResult(managerId, expeditionId, commandId, digest);
        if (duplicate != null) {
            return duplicate;
        }
        RunState current = requireRun(managerId, expeditionId);
        if (current.stateVersion() != expectedVersion) {
            throw new RunConflictException("Expedition version is stale.");
        }
        RunState updated = switch (current.phase()) {
            case FIGHTING -> current.requestReturn();
            case AWAITING_CONTINUE, WIPED, DUNGEON_COMPLETED -> current.returnBetweenFights();
            case SETTLEMENT_PENDING -> throw new RunConflictException("Expedition is already returning.");
        };
        Result result = store.command(current, updated, commandId, digest);
        if (result == Result.UPDATED) {
            repairSchedule(updated);
            return updated;
        }
        if (result == Result.DUPLICATE) {
            RunState existing = requireRun(managerId, expeditionId);
            repairSchedule(existing);
            return existing;
        }
        throw new RunConflictException("Cannot return Expedition: " + result);
    }

    private void repairSchedule(RunState run) {
        if (run.phase() == Phase.FIGHTING) {
            store.schedule(run, Instant.now());
        } else if (run.phase() == Phase.SETTLEMENT_PENDING) {
            settlements.schedule(run, Instant.now());
        }
    }

    private RunState priorResult(UUID managerId, UUID expeditionId, UUID commandId, String digest) {
        String receipt = store.receipt(managerId, commandId);
        if (receipt == null) {
            return null;
        }
        if (!receipt.startsWith(digest + ":")) {
            throw new RunConflictException("Command ID was reused with a different payload.");
        }
        return requireRun(managerId, expeditionId);
    }

    private RunState requireRun(UUID managerId, UUID expeditionId) {
        RunState run = store.get(managerId, expeditionId);
        if (run == null) {
            throw new RunNotFoundException(expeditionId);
        }
        return run;
    }

    private String digest(String action, Object input) {
        try {
            byte[] payload = digestMapper.writeValueAsBytes(input);
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(action.getBytes(StandardCharsets.UTF_8));
            sha256.update((byte) 0);
            return HexFormat.of().formatHex(sha256.digest(payload));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot digest an Expedition command.", exception);
        }
    }

    private void requireCommandId(UUID commandId) {
        if (commandId == null || commandId.version() != 7) {
            throw new IllegalArgumentException("Expedition commands need a UUIDv7 ID.");
        }
    }

    public static final class RunConflictException extends RuntimeException {
        public RunConflictException(String message) {
            super(message);
        }
    }

    public static final class RunNotFoundException extends RuntimeException {
        public RunNotFoundException(UUID id) {
            super("Expedition not found: " + id);
        }
    }
}
