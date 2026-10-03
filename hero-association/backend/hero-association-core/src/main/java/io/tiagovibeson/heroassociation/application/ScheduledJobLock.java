package io.tiagovibeson.heroassociation.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ScheduledJobLock {

    private static final int LOCK_NAMESPACE = 0x4845524F; // "HERO"

    @Inject
    EntityManager entityManager;

    @Transactional(Transactional.TxType.MANDATORY)
    public boolean tryAcquire(Job job) {
        return Boolean.TRUE.equals(entityManager
                .createNativeQuery("select pg_try_advisory_xact_lock(:namespace, :jobId)")
                .setParameter("namespace", LOCK_NAMESPACE)
                .setParameter("jobId", job.id)
                .getSingleResult());
    }

    public enum Job {
        AGENCY_RECOVERY(1);

        private final int id;

        Job(int id) {
            this.id = id;
        }
    }
}
