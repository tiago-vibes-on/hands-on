package io.tiagovibeson.heroassociation.assets.application;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import io.tiagovibeson.heroassociation.assets.domain.*;
import io.tiagovibeson.heroassociation.assets.application.exception.AssetOperationRejectedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class AssetBalances {
    @Inject EntityManager em;

    /** Separate advisory namespace; acquire all owner locks before locking any resource row. */
    public void lockOwners(Collection<UUID> owners) {
        owners.stream().distinct().mapToInt(UUID::hashCode).distinct().sorted().forEach(key -> em
                .createNativeQuery("select 1 from pg_advisory_xact_lock(584017, :key)", Integer.class)
                .setParameter("key", key).getSingleResult());
    }
    public AssetWallet wallet(AssetOwnerType type, UUID owner) {
        if (type == null || owner == null || owner.version() != 7 || owner.variant() != 2)
            throw new AssetOperationRejectedException(400, "Owner must be an RFC 9562 UUIDv7.");
        lockOwners(List.of(owner));
        AssetWallet wallet = em.find(AssetWallet.class, owner, LockModeType.PESSIMISTIC_WRITE);
        if (wallet == null) { wallet = new AssetWallet(type, owner); em.persist(wallet); em.flush(); }
        if (wallet.ownerType != type) throw new AssetOperationRejectedException("Owner ID has a different owner type.");
        return wallet;
    }
    public int quantity(UUID owner, String type, UUID resource) {
        return em.createQuery("select s.quantity from AssetStack s where s.ownerId = :owner and s.resourceType = :type and s.resourceId = :resource", Integer.class)
                .setParameter("owner", owner).setParameter("type", type).setParameter("resource", resource)
                .getResultStream().findFirst().orElse(0);
    }
    public void gold(AssetWallet wallet, long delta, UUID operation) {
        long after;
        try { after = Math.addExact(wallet.gold, delta); }
        catch (ArithmeticException failure) { throw new AssetOperationRejectedException("Gold balance would overflow."); }
        if (after < 0) throw new AssetOperationRejectedException("The owner does not have enough gold.");
        post(operation, wallet.ownerType.name(), wallet.ownerId, "AVAILABLE", "GOLD", null, wallet.gold, after);
        wallet.gold = after;
    }
    public void stack(AssetWallet wallet, String type, UUID resource, long delta, UUID operation) {
        AssetStack entry = em.createQuery("select s from AssetStack s where s.ownerId = :owner and s.resourceType = :type and s.resourceId = :resource", AssetStack.class)
                .setParameter("owner", wallet.ownerId).setParameter("type", type).setParameter("resource", resource)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst().orElse(null);
        int before = entry == null ? 0 : entry.quantity;
        long after;
        try { after = Math.addExact((long) before, delta); }
        catch (ArithmeticException failure) { throw new AssetOperationRejectedException("Inventory quantity would overflow."); }
        if (after < 0 || after > Integer.MAX_VALUE) throw new AssetOperationRejectedException("Inventory quantity is unavailable or would overflow.");
        if (entry == null) { entry = new AssetStack(wallet.ownerId, type, resource, (int) after); em.persist(entry); }
        else entry.quantity = (int) after;
        post(operation, wallet.ownerType.name(), wallet.ownerId, "AVAILABLE", type, resource, before, after);
    }
    public void post(UUID key, String ownerType, UUID owner, String scope, String resourceType, UUID resource, long before, long after) {
        if (before != after) em.persist(new AssetLedgerEntry(key, ownerType, owner, scope, resourceType, resource, before, after));
    }
}
