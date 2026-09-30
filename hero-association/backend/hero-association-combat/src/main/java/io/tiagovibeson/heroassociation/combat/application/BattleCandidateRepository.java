package io.tiagovibeson.heroassociation.combat.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BattleCandidateRepository {

    private static final int MAX_BATTLES_PER_TICK = 100;

    private final EntityManager entityManager;

    public BattleCandidateRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public List<UUID> dueIds(Instant now) {
        return entityManager.createQuery("""
                select b.id from BattleRecord b
                where b.status in ('PREPARED', 'IN_PROGRESS') and b.lastAdvancedAt < :now
                order by b.lastAdvancedAt, b.id
                """, UUID.class)
                .setParameter("now", now)
                .setMaxResults(MAX_BATTLES_PER_TICK)
                .getResultList();
    }
}
