package io.tiagovibeson.heroassociation.assets.application;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import io.tiagovibeson.heroassociation.assets.domain.*;
import io.tiagovibeson.heroassociation.assets.application.AssetSnapshots.RuneSlot;
import io.tiagovibeson.heroassociation.assets.application.exception.AssetOperationRejectedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

/** Executes an immutable, already-authorized Core workflow command in one Assets transaction. */
@ApplicationScoped
public class CoreAssetCommands {
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @Inject AssetCommandLocks keys;
    @Inject AssetBalances balances;
    @Inject AssetSnapshots snapshots;

    @Transactional public CommandReceipt execute(Command command) {
        if (command == null) throw new AssetOperationRejectedException(400, "Command is required.");
        requireId(command.operationKey()); requireId(command.managerId());
        if (command.kind() == null) throw new AssetOperationRejectedException(400, "Command kind is required.");
        keys.lock(command.operationKey());
        JsonNode request = json(encode(command));
        AssetCommandReceipt replay = em.find(AssetCommandReceipt.class, command.operationKey());
        if (replay != null) {
            if (!command.kind().equals(replay.kind) || !request.equals(json(replay.requestJson)))
                throw new AssetOperationRejectedException("Operation key was already used for a different command.");
            return decode(replay.responseJson);
        }
        List<UUID> owners = new ArrayList<>();
        owners.add(command.managerId());
        if (command.agencyId() != null) { requireId(command.agencyId()); owners.add(command.agencyId()); }
        if (command.heroId() != null) { requireId(command.heroId()); owners.add(command.heroId()); }
        if (command.heroIds() != null) {
            if (command.heroIds().size() > 4) throw new AssetOperationRejectedException(400, "A run supports at most four Heroes.");
            command.heroIds().forEach(this::requireId); owners.addAll(command.heroIds());
        }
        balances.lockOwners(owners);
        CommandReceipt result = switch (command.kind()) {
            case "RUNE_EQUIP", "RUNE_UNEQUIP" -> equipment(command, request);
            case "QUEST_START" -> quest(command, request);
            case "EXPEDITION_CREDIT" -> reward(command, request);
            case "HERO_LOADOUT_SNAPSHOT" -> applied(command, request, snapshots.loadouts(command.heroIds()));
            default -> throw new AssetOperationRejectedException(400, "Unsupported Core asset command.");
        };
        em.persist(new AssetCommandReceipt(command.operationKey(), command.kind(), request.toString(), encode(result)));
        em.flush();
        return result;
    }
    @Transactional public CommandReceipt receipt(UUID id) {
        requireId(id);
        AssetCommandReceipt receipt = em.find(AssetCommandReceipt.class, id);
        if (receipt == null || "GOLD_TRANSFER".equals(receipt.kind)) throw new NotFoundException();
        return decode(receipt.responseJson);
    }
    private CommandReceipt equipment(Command command, JsonNode request) {
        if (command.heroId() == null || command.agencyId() == null || command.slotIndex() == null || command.slotIndex() < 0 || command.slotIndex() >= 5)
            return rejected(command, request, 400, "Rune slot must be between zero and four.");
        HeroRune slot = em.createQuery("select r from HeroRune r where r.heroId = :hero and r.slotIndex = :slot", HeroRune.class)
                .setParameter("hero", command.heroId()).setParameter("slot", command.slotIndex())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst().orElse(null);
        if ("RUNE_EQUIP".equals(command.kind()) && slot != null && slot.rune.getId().equals(command.runeId()))
            return applied(command, request, snapshots.loadouts(List.of(command.heroId())));
        AssetWallet manager = balances.wallet(AssetOwnerType.MANAGER, command.managerId());
        Rune next = null;
        AssetWallet source = null;
        if ("RUNE_EQUIP".equals(command.kind())) {
            requireId(command.runeId());
            next = em.find(Rune.class, command.runeId());
            if (next == null) return rejected(command, request, 404, "The requested rune was not found.");
            source = switch (command.sourceOwnerType() == null ? "" : command.sourceOwnerType()) {
                case "MANAGER" -> manager;
                case "AGENCY" -> balances.wallet(AssetOwnerType.AGENCY, command.agencyId());
                default -> null;
            };
            if (source == null) return rejected(command, request, 400, "Rune source must be Manager or agency.");
            if (balances.quantity(source.ownerId, "RUNE", command.runeId()) < 1)
                return rejected(command, request, 404, "The requested rune is unavailable in that inventory.");
        }
        if (slot != null && balances.quantity(manager.ownerId, "RUNE", slot.rune.getId()) == Integer.MAX_VALUE
                && !(source == manager && slot.rune.getId().equals(command.runeId())))
            return rejected(command, request, 409, "The Manager rune inventory would overflow.");
        if (next != null) balances.stack(source, "RUNE", next.getId(), -1, command.operationKey());
        if (slot != null) {
            balances.stack(manager, "RUNE", slot.rune.getId(), 1, command.operationKey());
            balances.post(command.operationKey(), "HERO", command.heroId(), "SLOT_" + command.slotIndex(), "RUNE", slot.rune.getId(), 1, 0);
        }
        if (next == null) { if (slot != null) em.remove(slot); }
        else {
            if (slot == null) em.persist(new HeroRune(command.heroId(), command.slotIndex(), next));
            else slot.rune = next;
            balances.post(command.operationKey(), "HERO", command.heroId(), "SLOT_" + command.slotIndex(), "RUNE", next.getId(), 0, 1);
        }
        em.flush();
        return applied(command, request, snapshots.loadouts(List.of(command.heroId())));
    }
    private CommandReceipt quest(Command command, JsonNode request) {
        if (command.agencyId() == null || command.feeGold() == null || command.feeGold() < 0 || command.heroIds() == null || command.heroIds().isEmpty())
            return rejected(command, request, 400, "Quest payment and Hero snapshot are required.");
        AssetWallet manager = balances.wallet(AssetOwnerType.MANAGER, command.managerId());
        AssetWallet agency = balances.wallet(AssetOwnerType.AGENCY, command.agencyId());
        long fee = command.feeGold();
        if (manager.gold < fee) return rejected(command, request, 409, "The Manager does not have enough gold for the borrowing fee.");
        if (agency.gold > Long.MAX_VALUE - fee) return rejected(command, request, 409, "Agency gold balance would overflow.");
        var loadouts = snapshots.loadouts(command.heroIds());
        balances.gold(manager, -fee, command.operationKey());
        balances.gold(agency, fee, command.operationKey());
        return applied(command, request, loadouts);
    }
    private CommandReceipt reward(Command command, JsonNode request) {
        if (command.gold() == null || command.gold() < 0 || !validInventory(command.items()) || !validInventory(command.runes()))
            return rejected(command, request, 400, "Carried assets are invalid.");
        AssetWallet wallet = balances.wallet(AssetOwnerType.MANAGER, command.managerId());
        if (wallet.gold > Long.MAX_VALUE - command.gold()) throw new AssetOperationRejectedException(503, "Gold capacity must be freed before Expedition credit can complete.");
        for (var entry : command.items().entrySet()) {
            if (em.find(Item.class, entry.getKey()) == null) return rejected(command, request, 400, "Unknown carried item.");
            if (balances.quantity(wallet.ownerId, "ITEM", entry.getKey()) > Integer.MAX_VALUE - entry.getValue())
                throw new AssetOperationRejectedException(503, "Item capacity must be freed before Expedition credit can complete.");
        }
        for (var entry : command.runes().entrySet()) {
            if (em.find(Rune.class, entry.getKey()) == null) return rejected(command, request, 400, "Unknown carried rune.");
            if (balances.quantity(wallet.ownerId, "RUNE", entry.getKey()) > Integer.MAX_VALUE - entry.getValue())
                throw new AssetOperationRejectedException(503, "Rune capacity must be freed before Expedition credit can complete.");
        }
        balances.gold(wallet, command.gold(), command.operationKey());
        command.items().forEach((id, amount) -> { if (amount > 0) balances.stack(wallet, "ITEM", id, amount, command.operationKey()); });
        command.runes().forEach((id, amount) -> { if (amount > 0) balances.stack(wallet, "RUNE", id, amount, command.operationKey()); });
        return applied(command, request, Map.of());
    }
    private boolean validInventory(Map<UUID, Integer> inventory) {
        return inventory != null && inventory.size() <= 128 && inventory.entrySet().stream()
                .allMatch(entry -> entry.getKey() != null && entry.getKey().version() == 7 && entry.getKey().variant() == 2 && entry.getValue() != null && entry.getValue() >= 0);
    }
    private CommandReceipt applied(Command command, JsonNode request, Map<UUID, List<RuneSlot>> heroes) {
        return new CommandReceipt(command.operationKey(), command.kind(), "APPLIED", null, null, request, heroes);
    }
    private CommandReceipt rejected(Command command, JsonNode request, int status, String message) {
        return new CommandReceipt(command.operationKey(), command.kind(), "REJECTED", status, message, request, Map.of());
    }
    private void requireId(UUID id) {
        if (id == null || id.version() != 7 || id.variant() != 2) throw new AssetOperationRejectedException(400, "IDs must be RFC 9562 UUIDv7 values.");
    }
    private JsonNode json(String value) {
        try { return mapper.readTree(value); } catch (java.io.IOException failure) { throw new IllegalStateException("Stored command JSON is invalid.", failure); }
    }
    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); } catch (java.io.IOException failure) { throw new IllegalStateException("Command receipt could not be encoded.", failure); }
    }
    private CommandReceipt decode(String value) {
        try { return mapper.readValue(value, CommandReceipt.class); } catch (java.io.IOException failure) { throw new IllegalStateException("Stored command receipt is invalid.", failure); }
    }
    public record Command(UUID operationKey, String kind, UUID managerId, UUID agencyId, UUID heroId, Integer slotIndex,
                          UUID runeId, String sourceOwnerType, Long feeGold, List<UUID> heroIds, Long gold,
                          Map<UUID, Integer> items, Map<UUID, Integer> runes) { }
    public record CommandReceipt(UUID operationKey, String kind, String status, Integer rejectionStatus, String message,
                                 JsonNode request, Map<UUID, List<RuneSlot>> heroes) { }
}
