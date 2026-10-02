package io.tiagovibeson.heroassociation.assets.application;

import java.util.*;
import io.tiagovibeson.heroassociation.assets.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class AssetSnapshots {
    @Inject EntityManager em;
    @Transactional public Snapshot read(SnapshotRequest request) {
        if (request == null || request.owners() == null || request.heroes() == null || request.owners().size() > 128 || request.heroes().size() > 256)
            throw new BadRequestException("Snapshot bounds are invalid.");
        var owners = request.owners().stream().map(owner -> {
            requireId(owner.ownerId());
            AssetWallet wallet = em.find(AssetWallet.class, owner.ownerId());
            if (owner.ownerType() == null || wallet != null && wallet.ownerType != owner.ownerType()) throw new BadRequestException("Owner type differs from wallet.");
            List<AssetStack> stacks = em.createQuery("select s from AssetStack s where s.ownerId = :id order by s.resourceId", AssetStack.class)
                    .setParameter("id", owner.ownerId()).getResultList();
            return new OwnerSnapshot(owner.ownerType(), owner.ownerId(), wallet == null ? 0 : wallet.gold,
                    stacks.stream().filter(s -> "ITEM".equals(s.resourceType)).map(s -> new ItemQuantity(item(s.resourceId), s.quantity)).toList(),
                    stacks.stream().filter(s -> "RUNE".equals(s.resourceType)).map(s -> new RuneQuantity(rune(s.resourceId), s.quantity)).toList());
        }).toList();
        return new Snapshot(owners, loadouts(request.heroes()));
    }
    @Transactional public Map<UUID, List<RuneSlot>> loadouts(List<UUID> heroes) {
        if (heroes == null || heroes.size() > 256) throw new BadRequestException("Hero snapshot bounds are invalid.");
        Map<UUID, List<RuneSlot>> result = new LinkedHashMap<>();
        for (UUID hero : heroes.stream().distinct().toList()) {
            requireId(hero);
            result.put(hero, em.createQuery("select r from HeroRune r where r.heroId = :id order by r.slotIndex", HeroRune.class)
                    .setParameter("id", hero).getResultList().stream().map(slot -> new RuneSlot(slot.slotIndex, RuneDefinition.from(slot.rune))).toList());
        }
        return result;
    }
    @Transactional public ItemDefinition item(UUID id) {
        requireId(id); return ItemDefinition.from(Optional.ofNullable(em.find(Item.class, id)).orElseThrow(NotFoundException::new));
    }
    @Transactional public RuneDefinition rune(UUID id) {
        requireId(id); return RuneDefinition.from(Optional.ofNullable(em.find(Rune.class, id)).orElseThrow(NotFoundException::new));
    }
    private void requireId(UUID id) {
        if (id == null || id.version() != 7 || id.variant() != 2) throw new BadRequestException("IDs must be RFC 9562 UUIDv7 values.");
    }
    public record OwnerRequest(AssetOwnerType ownerType, UUID ownerId) { }
    public record SnapshotRequest(List<OwnerRequest> owners, List<UUID> heroes) { }
    public record Snapshot(List<OwnerSnapshot> owners, Map<UUID, List<RuneSlot>> heroes) { }
    public record OwnerSnapshot(AssetOwnerType ownerType, UUID ownerId, long gold, List<ItemQuantity> items, List<RuneQuantity> runes) { }
    public record ItemQuantity(ItemDefinition item, int quantity) { }
    public record RuneQuantity(RuneDefinition rune, int quantity) { }
    public record RuneSlot(int slotIndex, RuneDefinition rune) { }
    public record ItemDefinition(UUID id, String code, String name, String symbol, String description) {
        static ItemDefinition from(Item item) { return new ItemDefinition(item.getId(), item.getCode(), item.getName(), item.getSymbol(), item.getDescription()); }
    }
    public record RuneDefinition(UUID id, String code, String name, String symbol, String stats, String description, String effect, double effectValue) {
        static RuneDefinition from(Rune rune) { return new RuneDefinition(rune.getId(), rune.getCode(), rune.getName(), rune.getSymbol(), rune.getStats(), rune.getDescription(), rune.getEffect().name(), rune.getEffectValue()); }
    }
}
