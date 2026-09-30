package io.tiagovibeson.heroassociation.expedition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyScanArgs;
import io.vertx.mutiny.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;

/** Pending markers are authoritative; this global due index is only a repairable hint. */
@ApplicationScoped
public class RedisSettlementStore {

    private static final String PREFIX = "ha:expedition:v1:";
    private static final String DUE = PREFIX + "settlement-due";
    private static final String RELEASE_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """;
    private static final String PUBLISHED_SCRIPT = """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] or redis.call('GET', KEYS[2]) ~= ARGV[1] then
              return 'MISSING'
            end
            local raw = redis.call('GET', KEYS[3])
            if not raw then return 'MISSING' end
            local run = cjson.decode(raw)
            if run.phase ~= 'SETTLEMENT_PENDING' or tonumber(run.stateVersion) ~= tonumber(ARGV[2]) then
              return 'STALE'
            end
            redis.call('SET', KEYS[4], ARGV[3])
            return 'CONFIRMED'
            """;
    private static final String COMPLETE_SCRIPT = """
            local closed = redis.call('GET', KEYS[5])
            if closed then
              if closed == ARGV[3] then return 'DUPLICATE' end
              return 'DIGEST_CONFLICT'
            end
            if redis.call('GET', KEYS[1]) ~= ARGV[1] or redis.call('GET', KEYS[2]) ~= ARGV[1] then
              return 'MISSING'
            end
            local raw = redis.call('GET', KEYS[3])
            if not raw then return 'MISSING' end
            local run = cjson.decode(raw)
            if run.phase ~= 'SETTLEMENT_PENDING' or tonumber(run.stateVersion) ~= tonumber(ARGV[2]) then
              return 'STALE'
            end
            redis.call('DEL', KEYS[1], KEYS[2], KEYS[3], KEYS[4])
            redis.call('SETEX', KEYS[5], 604800, ARGV[3])
            return 'CLOSED'
            """;

    private final RedisDataSource redis;
    private final RedisRunStore runs;

    public RedisSettlementStore(RedisDataSource redis, RedisRunStore runs) {
        this.redis = redis;
        this.runs = runs;
    }

    public void schedule(RunState run, Instant when) {
        if (run.phase() != RunState.Phase.SETTLEMENT_PENDING) {
            throw new IllegalArgumentException("Only pending settlements can be scheduled.");
        }
        redis.execute("ZADD", DUE, Long.toString(when.toEpochMilli()),
                new Member(run.ownerManagerId(), run.expeditionId()).encode());
    }

    public void schedule(Member member, Instant when) {
        redis.execute("ZADD", DUE, Long.toString(when.toEpochMilli()), member.encode());
    }

    public List<Member> due(Instant now, int limit) {
        Response response = redis.execute("ZRANGEBYSCORE", DUE, "-inf", Long.toString(now.toEpochMilli()),
                "LIMIT", "0", Integer.toString(limit));
        List<Member> result = new ArrayList<>();
        if (response != null) {
            for (Response item : response) {
                result.add(Member.parse(item.toString()));
            }
        }
        return result;
    }

    public void unschedule(Member member) {
        redis.execute("ZREM", DUE, member.encode());
    }

    public boolean claim(Member member, String token) {
        Response result = redis.execute("SET", lease(member), token, "NX", "PX", "30000");
        return result != null && "OK".equals(result.toString());
    }

    public void release(Member member, String token) {
        redis.execute("EVAL", RELEASE_SCRIPT, "1", lease(member), token);
    }

    public void confirmed(RunState run, String digest) {
        Response result = redis.execute("EVAL", PUBLISHED_SCRIPT, "4",
                active(run.ownerManagerId()), pending(run.ownerManagerId()),
                snapshot(run.ownerManagerId(), run.expeditionId()), publication(run.ownerManagerId()),
                run.expeditionId().toString(), Long.toString(run.stateVersion()), digest);
        if (result == null || !"CONFIRMED".equals(result.toString())) {
            throw new IllegalStateException("Settlement publish confirmation could not be retained: " + result);
        }
    }

    public String closedDigest(UUID managerId, UUID expeditionId) {
        Response value = redis.execute("GET", closed(managerId, expeditionId));
        return value == null ? null : value.toString();
    }

    public String publishedDigest(UUID managerId) {
        Response value = redis.execute("GET", publication(managerId));
        return value == null ? null : value.toString();
    }

    /** Caller must verify the owner's application acknowledgment against the frozen payload first. */
    public boolean complete(RunState run, String digest) {
        Response result = redis.execute("EVAL", COMPLETE_SCRIPT, "5",
                active(run.ownerManagerId()), pending(run.ownerManagerId()),
                snapshot(run.ownerManagerId(), run.expeditionId()), publication(run.ownerManagerId()),
                closed(run.ownerManagerId(), run.expeditionId()), run.expeditionId().toString(), Long.toString(run.stateVersion()), digest);
        if (result == null) {
            throw new IllegalStateException("Settlement close returned no result.");
        }
        if ("CLOSED".equals(result.toString()) || "DUPLICATE".equals(result.toString())) {
            unschedule(new Member(run.ownerManagerId(), run.expeditionId()));
            return "CLOSED".equals(result.toString());
        }
        throw new IllegalStateException("Settlement close rejected: " + result);
    }

    public int rebuildDueIndex() {
        int count = 0;
        for (String key : redis.key().scan(new KeyScanArgs().match(PREFIX + "*:settlement-pending").count(128))
                .toIterable()) {
            Response id = redis.execute("GET", key);
            if (id == null) {
                continue;
            }
            UUID managerId = UUID.fromString(key.substring(key.indexOf('{') + 1, key.indexOf('}')));
            UUID expeditionId = UUID.fromString(id.toString());
            RunState run = runs.get(managerId, expeditionId);
            if (run == null || run.phase() != RunState.Phase.SETTLEMENT_PENDING) {
                throw new IllegalStateException("Pending settlement marker has no frozen run.");
            }
            schedule(run, Instant.now());
            count++;
        }
        return count;
    }

    private static String active(UUID managerId) { return PREFIX + "{" + managerId + "}:active"; }
    private static String pending(UUID managerId) { return PREFIX + "{" + managerId + "}:settlement-pending"; }
    private static String snapshot(UUID managerId, UUID expeditionId) {
        return PREFIX + "{" + managerId + "}:run:" + expeditionId;
    }
    private static String publication(UUID managerId) { return PREFIX + "{" + managerId + "}:publication"; }
    private static String closed(UUID managerId, UUID expeditionId) {
        return PREFIX + "{" + managerId + "}:closed:" + expeditionId;
    }
    private static String lease(Member member) {
        return PREFIX + "{" + member.managerId() + "}:settlement-lease:" + member.expeditionId();
    }

    public record Member(UUID managerId, UUID expeditionId) {
        String encode() { return managerId + "|" + expeditionId; }
        static Member parse(String value) {
            String[] fields = value.split("\\|", -1);
            if (fields.length != 2) {
                throw new IllegalStateException("Invalid settlement due-index entry.");
            }
            return new Member(UUID.fromString(fields[0]), UUID.fromString(fields[1]));
        }
    }
}
