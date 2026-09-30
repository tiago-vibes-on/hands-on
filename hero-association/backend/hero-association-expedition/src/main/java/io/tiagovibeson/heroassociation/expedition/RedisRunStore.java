package io.tiagovibeson.heroassociation.expedition;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyScanArgs;
import io.vertx.mutiny.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;

/** Atomic run/index/receipt writes; the sorted due index is only a repairable hint. */
@ApplicationScoped
public class RedisRunStore {

    private static final String PREFIX = "ha:expedition:v1:";
    private static final String DUE_KEY = PREFIX + "due";
    private static final int RECEIPT_SECONDS = 24 * 60 * 60;
    private static final long LEASE_MILLISECONDS = 30_000;

    private static final String START_SCRIPT = """
            if redis.call('EXISTS', KEYS[4]) == 1 then return 'CANCELLED' end
            local receipt = redis.call('GET', KEYS[3])
            if receipt then
              if string.sub(receipt, 1, 64) == ARGV[3] then return 'DUPLICATE' end
              return 'COMMAND_CONFLICT'
            end
            if redis.call('EXISTS', KEYS[1]) == 1 then return 'ACTIVE' end
            redis.call('SET', KEYS[1], ARGV[1])
            redis.call('SET', KEYS[2], ARGV[2])
            redis.call('SETEX', KEYS[3], ARGV[4], ARGV[3] .. ':' .. ARGV[1] .. ':1')
            return 'CREATED'
            """;

    private static final String CANCEL_ABSENT_SCRIPT = """
            if redis.call('EXISTS', KEYS[3]) == 1 then return 'CANCELLED' end
            if redis.call('EXISTS', KEYS[2]) == 1 then return 'RUN_EXISTS' end
            if redis.call('GET', KEYS[1]) == ARGV[1] then return 'RUN_EXISTS' end
            redis.call('SET', KEYS[3], ARGV[2])
            return 'CANCELLED'
            """;

    private static final String COMMAND_SCRIPT = """
            local receipt = redis.call('GET', KEYS[3])
            if receipt then
              if string.sub(receipt, 1, 64) == ARGV[3] then return 'DUPLICATE' end
              return 'COMMAND_CONFLICT'
            end
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 'MISSING' end
            local current = redis.call('GET', KEYS[2])
            if not current then return 'MISSING' end
            local old = cjson.decode(current)
            if tonumber(old.stateVersion) ~= tonumber(ARGV[4]) then return 'STALE' end
            redis.call('SET', KEYS[2], ARGV[2])
            if cjson.decode(ARGV[2]).phase == 'SETTLEMENT_PENDING' then
              redis.call('SET', KEYS[4], ARGV[1])
            end
            redis.call('SETEX', KEYS[3], ARGV[5], ARGV[3] .. ':' .. ARGV[1] .. ':' .. ARGV[6])
            return 'UPDATED'
            """;

    private static final String COMMIT_SCRIPT = """
            if redis.call('GET', KEYS[3]) ~= ARGV[4] then return 'LEASE_LOST' end
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 'MISSING' end
            local current = redis.call('GET', KEYS[2])
            if not current then return 'MISSING' end
            local old = cjson.decode(current)
            if old.phase ~= 'FIGHTING' or old.fight.fightId ~= ARGV[5] then return 'NOT_FIGHTING' end
            if tonumber(old.stateVersion) ~= tonumber(ARGV[3]) then return 'STALE' end
            redis.call('SET', KEYS[2], ARGV[2])
            if cjson.decode(ARGV[2]).phase == 'SETTLEMENT_PENDING' then
              redis.call('SET', KEYS[4], ARGV[1])
            end
            return 'UPDATED'
            """;

    private static final String RELEASE_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

    private final RedisDataSource redis;
    private final ObjectMapper mapper;

    public RedisRunStore(RedisDataSource redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public Result start(RunState run, UUID commandId, String payloadDigest) {
        return eval(START_SCRIPT, 4, activeKey(run.ownerManagerId()), runKey(run.ownerManagerId(), run.expeditionId()),
                commandKey(run.ownerManagerId(), commandId), cancelledKey(run.ownerManagerId(), run.expeditionId()),
                run.expeditionId().toString(), encode(run),
                payloadDigest, Integer.toString(RECEIPT_SECONDS));
    }

    /** Fences a reservation whose run is provably absent in this healthy Redis. */
    public boolean cancelIfAbsent(UUID managerId, UUID expeditionId) {
        Result result = eval(CANCEL_ABSENT_SCRIPT, 3, activeKey(managerId), runKey(managerId, expeditionId),
                cancelledKey(managerId, expeditionId), expeditionId.toString(), Instant.now().toString());
        return result == Result.CANCELLED;
    }

    public Result command(RunState old, RunState updated, UUID commandId, String payloadDigest) {
        return eval(COMMAND_SCRIPT, 4, activeKey(old.ownerManagerId()), runKey(old.ownerManagerId(), old.expeditionId()),
                commandKey(old.ownerManagerId(), commandId), pendingKey(old.ownerManagerId()),
                old.expeditionId().toString(), encode(updated),
                payloadDigest, Long.toString(old.stateVersion()), Integer.toString(RECEIPT_SECONDS),
                Long.toString(updated.stateVersion()));
    }

    public Result commitFight(RunState old, RunState updated, String leaseToken) {
        return eval(COMMIT_SCRIPT, 4, activeKey(old.ownerManagerId()), runKey(old.ownerManagerId(), old.expeditionId()),
                leaseKey(old.ownerManagerId(), old.expeditionId()), pendingKey(old.ownerManagerId()),
                old.expeditionId().toString(), encode(updated),
                Long.toString(old.stateVersion()), leaseToken, old.fight().fightId().toString());
    }

    public String receipt(UUID managerId, UUID commandId) {
        Response value = redis.execute("GET", commandKey(managerId, commandId));
        return value == null ? null : value.toString();
    }

    public RunState active(UUID managerId) {
        Response active = redis.execute("GET", activeKey(managerId));
        if (active == null) {
            return null;
        }
        UUID expeditionId;
        try {
            expeditionId = UUID.fromString(active.toString());
        } catch (IllegalArgumentException corrupted) {
            throw new IllegalStateException("Expedition active index is corrupt.", corrupted);
        }
        return get(managerId, expeditionId);
    }

    public RunState get(UUID managerId, UUID expeditionId) {
        Response active = redis.execute("GET", activeKey(managerId));
        if (active == null || !active.toString().equals(expeditionId.toString())) {
            return null;
        }
        Response state = redis.execute("GET", runKey(managerId, expeditionId));
        if (state == null) {
            throw new IllegalStateException("Expedition active index has no run snapshot.");
        }
        RunState run = decode(state.toString());
        if (!run.ownerManagerId().equals(managerId) || !run.expeditionId().equals(expeditionId)) {
            throw new IllegalStateException("Expedition Redis keys disagree with their snapshot identity.");
        }
        return run;
    }

    public void schedule(RunState run, Instant dueAt) {
        schedule(new Member(run.ownerManagerId(), run.expeditionId(), run.fight().fightId()), dueAt);
    }

    public void schedule(Member member, Instant dueAt) {
        redis.execute("ZADD", DUE_KEY, Long.toString(dueAt.toEpochMilli()), member.encode());
    }

    public void unschedule(Member member) {
        redis.execute("ZREM", DUE_KEY, member.encode());
    }

    public List<Member> due(Instant now, int limit) {
        Response response = redis.execute("ZRANGEBYSCORE", DUE_KEY, "-inf",
                Long.toString(now.toEpochMilli()), "LIMIT", "0", Integer.toString(limit));
        List<Member> due = new ArrayList<>();
        if (response != null) {
            for (Response item : response) {
                due.add(Member.parse(item.toString()));
            }
        }
        return due;
    }

    public boolean claim(Member member, String leaseToken) {
        Response result = redis.execute("SET", leaseKey(member.managerId(), member.expeditionId()),
                leaseToken, "NX", "PX", Long.toString(LEASE_MILLISECONDS));
        return result != null && "OK".equals(result.toString());
    }

    public void release(Member member, String leaseToken) {
        redis.execute("EVAL", RELEASE_SCRIPT, "1",
                leaseKey(member.managerId(), member.expeditionId()), leaseToken);
    }

    /** A restart repairs missing/non-authoritative due entries from the saved run boundaries. */
    public int rebuildDueIndex() {
        int rebuilt = 0;
        for (String key : redis.key().scan(new KeyScanArgs().match(PREFIX + "*:run:*").count(128)).toIterable()) {
            Response raw = redis.execute("GET", key);
            if (raw == null) {
                continue;
            }
            RunState run = decode(raw.toString());
            if (!key.equals(runKey(run.ownerManagerId(), run.expeditionId()))) {
                throw new IllegalStateException("Expedition run key does not match its snapshot.");
            }
            RunState active = get(run.ownerManagerId(), run.expeditionId());
            if (active == null) {
                throw new IllegalStateException("Expedition run has no matching active index.");
            }
            if (active.phase() == RunState.Phase.FIGHTING) {
                schedule(active, Instant.now());
                rebuilt++;
            }
        }
        return rebuilt;
    }

    private Result eval(String script, int keys, String... arguments) {
        String[] command = new String[arguments.length + 2];
        command[0] = script;
        command[1] = Integer.toString(keys);
        System.arraycopy(arguments, 0, command, 2, arguments.length);
        Response response = redis.execute("EVAL", command);
        return Result.valueOf(response.toString());
    }

    private String encode(RunState run) {
        try {
            return mapper.writeValueAsString(run);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot encode Expedition run.", exception);
        }
    }

    private RunState decode(String json) {
        try {
            return mapper.readValue(json, RunState.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot restore Expedition run; fail closed.", exception);
        }
    }

    private static String activeKey(UUID managerId) {
        return PREFIX + "{" + managerId + "}:active";
    }

    private static String runKey(UUID managerId, UUID expeditionId) {
        return PREFIX + "{" + managerId + "}:run:" + expeditionId;
    }

    private static String cancelledKey(UUID managerId, UUID expeditionId) {
        return PREFIX + "{" + managerId + "}:cancelled:" + expeditionId;
    }

    private static String commandKey(UUID managerId, UUID commandId) {
        return PREFIX + "{" + managerId + "}:command:" + commandId;
    }

    private static String pendingKey(UUID managerId) {
        return PREFIX + "{" + managerId + "}:settlement-pending";
    }

    private static String leaseKey(UUID managerId, UUID expeditionId) {
        return PREFIX + "{" + managerId + "}:lease:" + expeditionId;
    }

    public enum Result {
        CREATED,
        CANCELLED,
        RUN_EXISTS,
        DUPLICATE,
        ACTIVE,
        COMMAND_CONFLICT,
        UPDATED,
        STALE,
        MISSING,
        LEASE_LOST,
        NOT_FIGHTING
    }

    public record Member(UUID managerId, UUID expeditionId, UUID fightId) {
        String encode() {
            return managerId + "|" + expeditionId + "|" + fightId;
        }

        static Member parse(String encoded) {
            String[] parts = encoded.split("\\|", -1);
            if (parts.length != 3) {
                throw new IllegalStateException("Malformed Expedition due-index entry.");
            }
            return new Member(UUID.fromString(parts[0]), UUID.fromString(parts[1]), UUID.fromString(parts[2]));
        }
    }
}
