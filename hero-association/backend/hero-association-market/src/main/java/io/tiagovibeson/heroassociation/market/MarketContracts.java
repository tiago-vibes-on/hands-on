package io.tiagovibeson.heroassociation.market;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class MarketContracts {
    private MarketContracts() { }
    public enum OwnerType { MANAGER, AGENCY }
    public enum Side { BUY, SELL }
    public enum PlacementState { PENDING_RESERVATION, PENDING_ABORT, OPEN, REJECTED, ABANDONED, CONFLICT }
    public enum OrderState { OPEN, PARTIALLY_FILLED, FILLED, PENDING_CANCEL, CANCELLED, CONFLICT }
    public enum TradeState { PENDING_SETTLEMENT, SETTLED, CONFLICT }
    public record PlaceRequest(@NotNull UUID placementId, @NotNull OwnerType ownerType, UUID agencyId,
            @NotNull Side side, @NotNull UUID itemId, @Positive int quantity, @Positive long priceGoldPerItem) { }
    public record ContextRequest(OwnerType ownerType, UUID ownerId, UUID itemId) { }
    public record ItemDefinition(UUID id, String code, String name, String symbol) { }
    public record OwnerContext(UUID managerId, OwnerType ownerType, UUID ownerId, String ownerName, ItemDefinition item) { }
    public record ReserveRequest(UUID reservationKey, OwnerType ownerType, UUID ownerId, String resourceType,
                                 UUID itemId, int quantity, long unitPriceGoldPerItem) { }
    public record Reservation(UUID reservationKey, UUID requesterManagerId, OwnerType ownerType, UUID ownerId,
                              String resourceType, UUID itemId, int initialQuantity, int remainingQuantity,
                              long unitPriceGoldPerItem, boolean closed) { }
    public record SettlementRequest(UUID operationKey, UUID buyerReservationKey, UUID sellerReservationKey,
                                    int quantity, long executionPriceGoldPerItem) { }
    public record CloseRequest(UUID operationKey) { }
    public record Receipt(UUID operationKey, String kind, UUID firstReservationKey, UUID secondReservationKey,
                          int quantity, long executionPriceGoldPerItem) { }
    public record PlacementView(UUID id, String status, UUID orderId, String message) { }
    public record OrderView(UUID id, String ownerType, UUID ownerId, String ownerName, UUID itemId,
                            String itemCode, String itemName, String itemSymbol, String side, String status,
                            int quantityRemaining, int quantityPending, long priceGoldPerItem, Instant createdAt) {
        static OrderView from(MarketOrder order) {
            return new OrderView(order.id, order.ownerType.name(), order.ownerId, order.ownerName, order.itemId,
                    order.itemCode, order.itemName, order.itemSymbol, order.side.name(), order.state.name(),
                    order.quantityRemaining, order.quantityPending, order.priceGoldPerItem, order.createdAt);
        }
    }
    public record PlacementClaim(UUID id, UUID leaseId, PlacementState state, String requesterSubject,
            UUID requesterManagerId, UUID orderId, UUID reservationKey, UUID closureKey, OwnerType ownerType,
            UUID ownerId, String ownerName, UUID itemId, String itemCode, String itemName, String itemSymbol,
            Side side, int quantity, long priceGoldPerItem, int rejectionStatus, String message) { }
    public record TradeClaim(UUID id, UUID leaseId, UUID itemId, UUID buyerOrderId, UUID sellerOrderId,
                            UUID buyerReservationKey, UUID sellerReservationKey, int quantity, long priceGoldPerItem) { }
    public record CancellationClaim(UUID id, UUID leaseId, UUID itemId, UUID reservationKey, UUID closureKey,
                                     int quantityRemaining) { }
}
