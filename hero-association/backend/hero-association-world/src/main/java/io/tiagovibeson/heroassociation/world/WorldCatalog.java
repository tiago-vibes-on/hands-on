package io.tiagovibeson.heroassociation.world;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tiagovibeson.heroassociation.contract.WorldContract;
import io.tiagovibeson.heroassociation.contract.WorldContract.Creature;
import io.tiagovibeson.heroassociation.contract.WorldContract.Plan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

/** Reads one immutable catalog snapshot; publication is versioned seed data for this early-stage product. */
@ApplicationScoped
public class WorldCatalog {
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;

    @Transactional
    public List<WorldContract.MapDefinition> maps() {
        return em.createQuery("select m from MapDefinition m where m.version = (select max(v.version) from MapDefinition v where v.definitionId = m.definitionId) order by m.definitionId", MapDefinition.class)
                .getResultList().stream().map(this::map).toList();
    }

    @Transactional
    public List<Creature> creatures() {
        return em.createQuery("select c from CreatureDefinition c where c.version = (select max(v.version) from CreatureDefinition v where v.definitionId = c.definitionId) order by c.definitionId", CreatureDefinition.class)
                .getResultList().stream().map(this::creature).toList();
    }

    @Transactional
    public Creature creature(UUID id, int version) {
        WorldContract.id(id);
        var rows = em.createQuery("select c from CreatureDefinition c where c.definitionId = :id and c.version = :version", CreatureDefinition.class)
                .setParameter("id", id).setParameter("version", version).getResultList();
        if (rows.isEmpty()) throw new NotFoundException("Creature version is unavailable.");
        return creature(rows.getFirst());
    }

    @Transactional
    public Plan plan(UUID id, Integer version) {
        WorldContract.id(id);
        var rows = version == null
                ? em.createQuery("select m from MapDefinition m where m.definitionId = :id order by m.version desc", MapDefinition.class).setParameter("id", id).setMaxResults(1).getResultList()
                : em.createQuery("select m from MapDefinition m where m.definitionId = :id and m.version = :version", MapDefinition.class).setParameter("id", id).setParameter("version", version).getResultList();
        if (rows.isEmpty()) throw new NotFoundException("Map version is unavailable.");
        WorldContract.MapDefinition map = map(rows.getFirst());
        Map<String, Creature> pinned = new LinkedHashMap<>();
        for (var floor : map.floors()) for (var encounter : floor.encounters()) for (var spawn : encounter.spawns())
            pinned.computeIfAbsent(spawn.creatureId() + ":" + spawn.creatureVersion(), ignored -> creature(spawn.creatureId(), spawn.creatureVersion()));
        return new Plan(map, List.copyOf(pinned.values()));
    }

    private WorldContract.MapDefinition map(MapDefinition row) {
        var value = decode(row.payload, WorldContract.MapDefinition.class);
        if (!row.definitionId.equals(value.definitionId()) || row.version != value.version()) throw new IllegalStateException("Map catalog identity differs from its payload.");
        return value;
    }
    private Creature creature(CreatureDefinition row) {
        var value = decode(row.payload, Creature.class);
        if (!row.definitionId.equals(value.definitionId()) || row.version != value.version()) throw new IllegalStateException("Creature catalog identity differs from its payload.");
        return value;
    }
    private <T> T decode(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (java.io.IOException invalid) { throw new IllegalStateException("Invalid versioned World definition.", invalid); }
    }
}
