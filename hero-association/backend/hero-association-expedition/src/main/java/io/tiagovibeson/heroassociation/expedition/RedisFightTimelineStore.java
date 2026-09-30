package io.tiagovibeson.heroassociation.expedition;

import java.io.IOException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.vertx.mutiny.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;

/** One immutable plan per fight; Redis survives worker handoff between replicas. */
@ApplicationScoped
public class RedisFightTimelineStore {

    private static final String PREFIX = "ha:expedition:v1:";
    private static final int TTL_SECONDS = 60 * 60;

    private final RedisDataSource redis;
    private final ObjectMapper mapper;

    public RedisFightTimelineStore(RedisDataSource redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public FightTimeline get(RunState run) {
        if (run.fight() == null) return null;
        Response response = redis.execute("GET", key(run));
        if (response == null) return null;
        try {
            FightTimeline timeline = mapper.readValue(response.toString(), FightTimeline.class);
            if (!timeline.fightId().equals(run.fight().fightId())) {
                throw new IllegalStateException("Fight timeline identity does not match its Redis key.");
            }
            return timeline;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot restore fight timeline; fail closed.", exception);
        }
    }

    public FightTimeline saveIfAbsent(RunState run, FightTimeline timeline) {
        if (run.fight() == null || !timeline.fightId().equals(run.fight().fightId())) {
            throw new IllegalArgumentException("Timeline belongs to a different fight.");
        }
        try {
            redis.execute("SET", key(run), mapper.writeValueAsString(timeline),
                    "NX", "EX", Integer.toString(TTL_SECONDS));
            return get(run);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save fight timeline.", exception);
        }
    }

    public void remove(RunState run) {
        if (run.fight() != null) redis.execute("DEL", key(run));
    }

    private static String key(RunState run) {
        return PREFIX + "{" + run.ownerManagerId() + "}:fight:" + run.fight().fightId();
    }
}
