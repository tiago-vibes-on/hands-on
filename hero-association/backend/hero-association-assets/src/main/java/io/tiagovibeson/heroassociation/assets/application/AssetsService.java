package io.tiagovibeson.heroassociation.assets.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.tiagovibeson.heroassociation.assets.application.exception.AssetOperationRejectedException;
import io.tiagovibeson.heroassociation.assets.domain.AssetOperationKind;
import io.tiagovibeson.heroassociation.assets.domain.AssetOperationReceipt;
import io.tiagovibeson.heroassociation.assets.domain.AssetOwnerType;
import io.tiagovibeson.heroassociation.assets.domain.AssetReservation;
import io.tiagovibeson.heroassociation.assets.domain.AssetReservationClosure;
import io.tiagovibeson.heroassociation.assets.domain.AssetResourceType;
import io.tiagovibeson.heroassociation.assets.domain.Item;
import io.tiagovibeson.heroassociation.assets.repository.AssetOperationReceiptRepository;
import io.tiagovibeson.heroassociation.assets.repository.AssetReservationRepository;
import io.tiagovibeson.heroassociation.assets.repository.AssetReservationClosureRepository;
import io.tiagovibeson.heroassociation.assets.repository.ItemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Private reservation boundary for Market.
 * Callers must authenticate and authorize the requester before invoking it.
 */
@ApplicationScoped
public class AssetsService {

    @Inject AssetReservationRepository reservationRepository;
    @Inject AssetReservationClosureRepository closureRepository;
    @Inject AssetCommandLocks commandLocks;
    @Inject AssetOperationReceiptRepository receiptRepository;
    @Inject AssetBalances balances;
    @Inject ItemRepository itemRepository;
    @Inject EntityManager entityManager;

    @Transactional
    public AssetReservation reserveGold(UUID reservationKey, AssetOwnerType ownerType,
            UUID ownerId, UUID itemId, int quantity, long maximumPriceGoldPerItem) {
        return reserve(reservationKey, ownerType, ownerId, AssetResourceType.GOLD,
                itemId, quantity, maximumPriceGoldPerItem, null);
    }

    @Transactional
    public AssetReservation reserveItems(UUID reservationKey, AssetOwnerType ownerType,
            UUID ownerId, UUID itemId, int quantity, long minimumPriceGoldPerItem) {
        return reserve(reservationKey, ownerType, ownerId, AssetResourceType.ITEM,
                itemId, quantity, minimumPriceGoldPerItem, null);
    }

    @Transactional
    public AssetReservation reserveAuthorized(UUID reservationKey, AssetOwnerType ownerType,
            UUID ownerId, AssetResourceType resourceType, UUID itemId, int quantity,
            long unitPriceGoldPerItem, UUID requesterManagerId) {
        if (requesterManagerId == null || resourceType == null) {
            throw rejected("An authenticated requester and resource type are required.");
        }
        return reserve(reservationKey, ownerType, ownerId, resourceType,
                itemId, quantity, unitPriceGoldPerItem, requesterManagerId);
    }

    @Transactional
    public AssetOperationReceipt release(UUID operationKey, UUID reservationKey, int quantity) {
        requireV7(operationKey, "Operation key");
        requireV7(reservationKey, "Reservation key");
        requireQuantity(quantity);

        commandLocks.lock(operationKey);

        AssetReservation snapshot = findReservation(reservationKey);
        LockedOwner owner = lockOwner(new OwnerKey(snapshot.getOwnerType(), snapshot.getOwnerId()), operationKey);
        AssetOperationReceipt replay = receiptRepository.findByKey(operationKey).orElse(null);
        if (replay != null) {
            requireMatchingReceipt(replay, AssetOperationKind.RELEASE, reservationKey, null, quantity, 0);
            return replay;
        }

        AssetReservation reservation = lockReservation(reservationKey);
        requireAvailable(reservation, quantity);
        if (reservation.getResourceType() == AssetResourceType.GOLD) {
            creditGold(owner, value(reservation.getUnitPriceGoldPerItem(), quantity));
        } else {
            creditItem(owner, reservation.getItemId(), quantity);
        }
        reservation.consume(quantity);
        AssetOperationReceipt receipt = new AssetOperationReceipt(operationKey,
                AssetOperationKind.RELEASE, reservationKey, null, quantity, 0);
        receiptRepository.persistAndFlush(receipt);
        return receipt;
    }

    @Transactional
    public AssetOperationReceipt settleTrade(UUID operationKey, UUID buyerGoldReservationKey,
            UUID sellerItemReservationKey, int quantity, long executionPriceGoldPerItem) {
        requireV7(operationKey, "Operation key");
        requireV7(buyerGoldReservationKey, "Buyer reservation key");
        requireV7(sellerItemReservationKey, "Seller reservation key");
        requireQuantity(quantity);
        if (buyerGoldReservationKey.equals(sellerItemReservationKey) || executionPriceGoldPerItem <= 0) {
            throw rejected("A trade needs distinct reservations and a positive execution price.");
        }

        commandLocks.lock(operationKey);

        AssetReservation buyerSnapshot = findReservation(buyerGoldReservationKey);
        AssetReservation sellerSnapshot = findReservation(sellerItemReservationKey);
        balances.lockOwners(List.of(buyerSnapshot.getOwnerId(), sellerSnapshot.getOwnerId()));
        Map<OwnerKey, LockedOwner> owners = Stream.of(buyerSnapshot, sellerSnapshot)
                .map(reservation -> new OwnerKey(reservation.getOwnerType(), reservation.getOwnerId()))
                .distinct()
                .sorted(Comparator.comparing(OwnerKey::type).thenComparing(OwnerKey::id))
                .collect(Collectors.toMap(key -> key, key -> lockOwner(key, operationKey)));

        AssetOperationReceipt replay = receiptRepository.findByKey(operationKey).orElse(null);
        if (replay != null) {
            requireMatchingReceipt(replay, AssetOperationKind.TRADE_SETTLEMENT,
                    buyerGoldReservationKey, sellerItemReservationKey, quantity, executionPriceGoldPerItem);
            return replay;
        }

        List<UUID> keys = Stream.of(buyerGoldReservationKey, sellerItemReservationKey)
                .sorted().toList();
        AssetReservation first = lockReservation(keys.get(0));
        AssetReservation second = lockReservation(keys.get(1));
        AssetReservation buyer = first.getReservationKey().equals(buyerGoldReservationKey) ? first : second;
        AssetReservation seller = first.getReservationKey().equals(sellerItemReservationKey) ? first : second;
        if (buyer.getResourceType() != AssetResourceType.GOLD
                || seller.getResourceType() != AssetResourceType.ITEM
                || !buyer.getItemId().equals(seller.getItemId())
                || (buyer.getOwnerType() == seller.getOwnerType()
                        && buyer.getOwnerId().equals(seller.getOwnerId()))
                || executionPriceGoldPerItem > buyer.getUnitPriceGoldPerItem()
                || executionPriceGoldPerItem < seller.getUnitPriceGoldPerItem()) {
            throw rejected("Trade reservations are incompatible.");
        }
        requireAvailable(buyer, quantity);
        requireAvailable(seller, quantity);

        long gross = value(executionPriceGoldPerItem, quantity);
        long fee = gross / 10;
        long refund = value(buyer.getUnitPriceGoldPerItem() - executionPriceGoldPerItem, quantity);
        LockedOwner buyerOwner = owners.get(new OwnerKey(buyer.getOwnerType(), buyer.getOwnerId()));
        LockedOwner sellerOwner = owners.get(new OwnerKey(seller.getOwnerType(), seller.getOwnerId()));
        creditGold(buyerOwner, refund);
        creditGold(sellerOwner, gross - fee);
        creditItem(buyerOwner, buyer.getItemId(), quantity);
        buyer.consume(quantity);
        seller.consume(quantity);
        AssetOperationReceipt receipt = new AssetOperationReceipt(operationKey,
                AssetOperationKind.TRADE_SETTLEMENT, buyerGoldReservationKey,
                sellerItemReservationKey, quantity, executionPriceGoldPerItem);
        receiptRepository.persistAndFlush(receipt);
        return receipt;
    }

    @Transactional
    public AssetOperationReceipt close(UUID operationKey, UUID reservationKey) {
        requireV7(operationKey, "Operation key");
        requireV7(reservationKey, "Reservation key");
        commandLocks.lock(operationKey, reservationKey);
        AssetOperationReceipt replay = receiptRepository.findByKey(operationKey).orElse(null);
        if (replay != null) {
            requireMatchingReceipt(replay, AssetOperationKind.CLOSE, reservationKey, null,
                    replay.getQuantity(), 0);
            return replay;
        }
        AssetReservation snapshot = reservationRepository.findByKey(reservationKey).orElse(null);
        int quantity = 0;
        if (snapshot != null) {
            LockedOwner owner = lockOwner(new OwnerKey(snapshot.getOwnerType(), snapshot.getOwnerId()), operationKey);
            AssetReservation reservation = lockReservation(reservationKey);
            quantity = reservation.getRemainingQuantity();
            if (quantity > 0) {
                if (reservation.getResourceType() == AssetResourceType.GOLD) {
                    creditGold(owner, value(reservation.getUnitPriceGoldPerItem(), quantity));
                } else {
                    creditItem(owner, reservation.getItemId(), quantity);
                }
                reservation.consume(quantity);
            }
        }
        if (!closureRepository.exists(reservationKey)) {
            closureRepository.persist(new AssetReservationClosure(reservationKey));
        }
        AssetOperationReceipt receipt = new AssetOperationReceipt(operationKey,
                AssetOperationKind.CLOSE, reservationKey, null, quantity, 0);
        receiptRepository.persistAndFlush(receipt);
        return receipt;
    }

    private AssetReservation reserve(UUID reservationKey, AssetOwnerType ownerType,
            UUID ownerId, AssetResourceType resourceType, UUID itemId, int quantity,
            long unitPriceGoldPerItem, UUID requesterManagerId) {
        requireV7(reservationKey, "Reservation key");
        commandLocks.lock(reservationKey);
        if (closureRepository.exists(reservationKey)) {
            throw rejected("The reservation was permanently closed.");
        }
        if (ownerType == null || ownerId == null || itemId == null) {
            throw rejected("An owner and item are required.");
        }
        requireQuantity(quantity);
        if (unitPriceGoldPerItem <= 0) {
            throw rejected("Reservation price must be positive.");
        }
        Item item = itemRepository.findByIdOptional(itemId)
                .orElseThrow(() -> rejected("The requested item does not exist."));
        LockedOwner owner = lockOwner(new OwnerKey(ownerType, ownerId), reservationKey);
        AssetReservation existing = reservationRepository.findByKey(reservationKey).orElse(null);
        if (existing != null) {
            if (existing.getOwnerType() != ownerType || !existing.getOwnerId().equals(ownerId)
                    || existing.getResourceType() != resourceType || !existing.getItemId().equals(itemId)
                    || existing.getInitialQuantity() != quantity
                    || !Objects.equals(existing.getRequesterManagerId(), requesterManagerId)
                    || existing.getUnitPriceGoldPerItem() != unitPriceGoldPerItem) {
                throw rejected("Reservation key was already used for a different request.");
            }
            return existing;
        }

        if (resourceType == AssetResourceType.GOLD) {
            long amount = value(unitPriceGoldPerItem, quantity);
            if (owner.gold() < amount) {
                throw rejected("The owner does not have enough gold to reserve.");
            }
            owner.debitGold(amount);
        } else {
            debitItem(owner, item, quantity);
        }
        AssetReservation reservation = new AssetReservation(reservationKey, ownerType,
                ownerId, resourceType, itemId, quantity, unitPriceGoldPerItem, requesterManagerId);
        reservationRepository.persistAndFlush(reservation);
        return reservation;
    }

    private void debitItem(LockedOwner owner, Item item, int quantity) {
        balances.stack(owner.wallet, "ITEM", item.getId(), -quantity, owner.operationKey);
    }

    private void creditItem(LockedOwner owner, UUID itemId, int quantity) {
        itemRepository.findByIdOptional(itemId).orElseThrow(() -> rejected("The reserved item no longer exists."));
        balances.stack(owner.wallet, "ITEM", itemId, quantity, owner.operationKey);
    }

    private LockedOwner lockOwner(OwnerKey key, UUID operationKey) {
        return new LockedOwner(balances.wallet(key.type(), key.id()), operationKey);
    }

    private AssetReservation findReservation(UUID key) {
        return reservationRepository.findByKey(key)
                .orElseThrow(() -> rejected("The reservation does not exist."));
    }

    private AssetReservation lockReservation(UUID key) {
        AssetReservation reservation = reservationRepository.findByKeyForUpdate(key)
                .orElseThrow(() -> rejected("The reservation does not exist."));
        // A pre-lock owner lookup may have loaded this row before another transaction committed.
        entityManager.refresh(reservation);
        return reservation;
    }

    private void requireMatchingReceipt(AssetOperationReceipt receipt, AssetOperationKind kind,
            UUID firstKey, UUID secondKey, int quantity, long price) {
        if (receipt.getKind() != kind || !receipt.getFirstReservationKey().equals(firstKey)
                || !Objects.equals(receipt.getSecondReservationKey(), secondKey)
                || receipt.getQuantity() != quantity || receipt.getExecutionPriceGoldPerItem() != price) {
            throw rejected("Operation key was already used for a different request.");
        }
    }

    private void requireAvailable(AssetReservation reservation, int quantity) {
        if (reservation.getRemainingQuantity() < quantity) {
            throw rejected("The reservation does not have enough remaining quantity.");
        }
    }

    private void requireV7(UUID key, String name) {
        if (key == null || key.version() != 7 || key.variant() != 2) {
            throw new AssetOperationRejectedException(400, name + " must be an RFC 9562 UUIDv7.");
        }
    }

    private void requireQuantity(int quantity) {
        if (quantity <= 0) {
            throw rejected("Quantity must be positive.");
        }
    }

    private long value(long unitPriceGoldPerItem, int quantity) {
        try {
            return Math.multiplyExact(unitPriceGoldPerItem, quantity);
        } catch (ArithmeticException exception) {
            throw rejected("Gold value would overflow.");
        }
    }

    private void creditGold(LockedOwner owner, long amount) {
        try {
            owner.creditGold(amount);
        } catch (ArithmeticException exception) {
            throw rejected("Gold balance would overflow.");
        }
    }

    private AssetOperationRejectedException rejected(String message) {
        return new AssetOperationRejectedException(message);
    }

    private record OwnerKey(AssetOwnerType type, UUID id) {
    }

    private class LockedOwner {
        final io.tiagovibeson.heroassociation.assets.domain.AssetWallet wallet;
        final UUID operationKey;
        LockedOwner(io.tiagovibeson.heroassociation.assets.domain.AssetWallet wallet, UUID key) {
            this.wallet = wallet; operationKey = key;
        }
        long gold() { return wallet.gold; }
        void debitGold(long amount) { balances.gold(wallet, -amount, operationKey); }
        void creditGold(long amount) { balances.gold(wallet, amount, operationKey); }
    }
}
