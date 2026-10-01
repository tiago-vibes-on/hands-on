package io.tiagovibeson.heroassociation.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.tiagovibeson.heroassociation.application.exception.AssetOperationRejectedException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.AgencyItem;
import io.tiagovibeson.heroassociation.domain.AssetOperationKind;
import io.tiagovibeson.heroassociation.domain.AssetOperationReceipt;
import io.tiagovibeson.heroassociation.domain.AssetOwnerType;
import io.tiagovibeson.heroassociation.domain.AssetReservation;
import io.tiagovibeson.heroassociation.domain.AssetResourceType;
import io.tiagovibeson.heroassociation.domain.Item;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.domain.ManagerItem;
import io.tiagovibeson.heroassociation.repository.AgencyItemRepository;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.AssetOperationReceiptRepository;
import io.tiagovibeson.heroassociation.repository.AssetReservationRepository;
import io.tiagovibeson.heroassociation.repository.ItemRepository;
import io.tiagovibeson.heroassociation.repository.ManagerItemRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Private Core-owned reservation boundary for the future Market service.
 * Callers must authenticate and authorize the requester before invoking it.
 */
@ApplicationScoped
public class AssetsService {

    @Inject AssetReservationRepository reservationRepository;
    @Inject AssetOperationReceiptRepository receiptRepository;
    @Inject ManagerRepository managerRepository;
    @Inject AgencyRepository agencyRepository;
    @Inject ManagerItemRepository managerItemRepository;
    @Inject AgencyItemRepository agencyItemRepository;
    @Inject ItemRepository itemRepository;
    @Inject EntityManager entityManager;

    @Transactional
    public AssetReservation reserveGold(UUID reservationKey, AssetOwnerType ownerType,
            UUID ownerId, UUID itemId, int quantity, long maximumPriceGoldPerItem) {
        return reserve(reservationKey, ownerType, ownerId, AssetResourceType.GOLD,
                itemId, quantity, maximumPriceGoldPerItem);
    }

    @Transactional
    public AssetReservation reserveItems(UUID reservationKey, AssetOwnerType ownerType,
            UUID ownerId, UUID itemId, int quantity, long minimumPriceGoldPerItem) {
        return reserve(reservationKey, ownerType, ownerId, AssetResourceType.ITEM,
                itemId, quantity, minimumPriceGoldPerItem);
    }

    @Transactional
    public AssetOperationReceipt release(UUID operationKey, UUID reservationKey, int quantity) {
        requireV7(operationKey, "Operation key");
        requireV7(reservationKey, "Reservation key");
        requireQuantity(quantity);

        AssetReservation snapshot = findReservation(reservationKey);
        LockedOwner owner = lockOwner(new OwnerKey(snapshot.getOwnerType(), snapshot.getOwnerId()));
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

        AssetReservation buyerSnapshot = findReservation(buyerGoldReservationKey);
        AssetReservation sellerSnapshot = findReservation(sellerItemReservationKey);
        Map<OwnerKey, LockedOwner> owners = Stream.of(buyerSnapshot, sellerSnapshot)
                .map(reservation -> new OwnerKey(reservation.getOwnerType(), reservation.getOwnerId()))
                .distinct()
                .sorted(Comparator.comparing(OwnerKey::type).thenComparing(OwnerKey::id))
                .collect(Collectors.toMap(key -> key, this::lockOwner));

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

    private AssetReservation reserve(UUID reservationKey, AssetOwnerType ownerType,
            UUID ownerId, AssetResourceType resourceType, UUID itemId, int quantity,
            long unitPriceGoldPerItem) {
        requireV7(reservationKey, "Reservation key");
        if (ownerType == null || ownerId == null || itemId == null) {
            throw rejected("An owner and item are required.");
        }
        requireQuantity(quantity);
        if (unitPriceGoldPerItem <= 0) {
            throw rejected("Reservation price must be positive.");
        }
        Item item = itemRepository.findByIdOptional(itemId)
                .orElseThrow(() -> rejected("The requested item does not exist."));
        LockedOwner owner = lockOwner(new OwnerKey(ownerType, ownerId));
        AssetReservation existing = reservationRepository.findByKey(reservationKey).orElse(null);
        if (existing != null) {
            if (existing.getOwnerType() != ownerType || !existing.getOwnerId().equals(ownerId)
                    || existing.getResourceType() != resourceType || !existing.getItemId().equals(itemId)
                    || existing.getInitialQuantity() != quantity
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
                ownerId, resourceType, itemId, quantity, unitPriceGoldPerItem);
        reservationRepository.persistAndFlush(reservation);
        return reservation;
    }

    private void debitItem(LockedOwner owner, Item item, int quantity) {
        if (owner.manager() != null) {
            ManagerItem entry = managerItemRepository.findForUpdate(owner.id(), item.getId())
                    .orElseThrow(() -> rejected("The Manager does not have this item."));
            if (entry.getQuantity() < quantity) {
                throw rejected("The Manager does not have enough of this item.");
            }
            entry.decreaseQuantity(quantity);
        } else {
            AgencyItem entry = agencyItemRepository.findForUpdate(owner.id(), item.getId())
                    .orElseThrow(() -> rejected("The agency does not have this item."));
            if (entry.getQuantity() < quantity) {
                throw rejected("The agency does not have enough of this item.");
            }
            entry.decreaseQuantity(quantity);
        }
    }

    private void creditItem(LockedOwner owner, UUID itemId, int quantity) {
        Item item = itemRepository.findByIdOptional(itemId)
                .orElseThrow(() -> rejected("The reserved item no longer exists."));
        try {
            if (owner.manager() != null) {
                ManagerItem entry = managerItemRepository.findForUpdate(owner.id(), itemId).orElse(null);
                if (entry == null) {
                    managerItemRepository.persist(new ManagerItem(owner.manager(), item, quantity));
                } else {
                    entry.increaseQuantity(quantity);
                }
            } else {
                AgencyItem entry = agencyItemRepository.findForUpdate(owner.id(), itemId).orElse(null);
                if (entry == null) {
                    agencyItemRepository.persist(new AgencyItem(owner.agency(), item, quantity));
                } else {
                    entry.increaseQuantity(quantity);
                }
            }
        } catch (ArithmeticException exception) {
            throw rejected("Item quantity would overflow.");
        }
    }

    private LockedOwner lockOwner(OwnerKey key) {
        if (key.type() == AssetOwnerType.MANAGER) {
            Manager manager = managerRepository.findForUpdate(key.id())
                    .orElseThrow(() -> rejected("The Manager does not exist."));
            return new LockedOwner(manager, null);
        }
        Agency agency = agencyRepository.findForUpdate(key.id())
                .orElseThrow(() -> rejected("The agency does not exist."));
        return new LockedOwner(null, agency);
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
        if (key == null || key.version() != 7) {
            throw rejected(name + " must be an RFC 9562 UUIDv7.");
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

    private record LockedOwner(Manager manager, Agency agency) {
        UUID id() {
            return manager != null ? manager.getId() : agency.getId();
        }

        long gold() {
            return manager != null ? manager.getGold() : agency.getGold();
        }

        void debitGold(long amount) {
            if (manager != null) {
                manager.decreaseGold(amount);
            } else {
                agency.decreaseGold(amount);
            }
        }

        void creditGold(long amount) {
            if (manager != null) {
                manager.increaseGold(amount);
            } else {
                agency.increaseGold(amount);
            }
        }
    }
}
