package io.tiagovibeson.heroassociation.market;

import java.util.UUID;
import io.tiagovibeson.heroassociation.market.AssetsClient.AssetsFailure;
import io.tiagovibeson.heroassociation.market.MarketContracts.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class MarketFacade {
    @Inject MarketTransactions transactions;
    @Inject MarketCoordinator coordinator;
    @Inject AssetsClient core;

    public Response place(String subject, String token, PlaceRequest request) {
        requireV7(request.placementId()); requireV7(request.itemId());
        if (request.ownerType() == OwnerType.AGENCY && request.agencyId() == null) throw new MarketException(400, "An agency order requires agencyId.");
        if (request.ownerType() == OwnerType.MANAGER && request.agencyId() != null) throw new MarketException(400, "A personal order must not include agencyId.");
        try { Math.multiplyExact(request.priceGoldPerItem(), request.quantity()); }
        catch (ArithmeticException failure) { throw new MarketException(400, "Order value exceeds the supported gold range."); }
        MarketPlacement existing = transactions.placement(request.placementId());
        if (existing != null) {
            if (!existing.matches(subject, request)) throw new MarketException(409, "Placement ID was already used for a different request.");
        } else {
            OwnerContext context = context(new ContextRequest(request.ownerType(), request.agencyId(), request.itemId()), token);
            if (context.managerId() == null || context.ownerType() != request.ownerType() || context.ownerId() == null
                    || context.ownerName() == null || context.item() == null || !request.itemId().equals(context.item().id())
                    || request.ownerType() == OwnerType.MANAGER && !context.managerId().equals(context.ownerId())
                    || request.ownerType() == OwnerType.AGENCY && !request.agencyId().equals(context.ownerId()))
                throw new MarketException(502, "Assets returned an inconsistent owner context.");
            transactions.stage(subject, request, context);
        }
        coordinator.placement(request.placementId(), token);
        MarketPlacement placement = transactions.placement(request.placementId());
        return switch (placement.state) {
            case OPEN -> Response.status(201).entity(transactions.order(placement.orderId)).build();
            case PENDING_RESERVATION, PENDING_ABORT -> Response.accepted(placement.view()).build();
            case REJECTED -> throw new MarketException(placement.rejectionStatus, placement.message);
            case ABANDONED, CONFLICT -> throw new MarketException(409, placement.message == null ? "Placement requires recovery." : placement.message);
        };
    }
    public PlacementView placement(String subject, UUID id) {
        requireV7(id);
        MarketPlacement placement = transactions.placement(id);
        if (placement == null) throw new MarketException(404, "The requested placement was not found.");
        if (!placement.requesterSubject.equals(subject)) throw new MarketException(403, "Only the initiating player can read this placement.");
        return placement.view();
    }
    public OrderView order(String token, UUID id) {
        requireV7(id);
        OrderView order = transactions.order(id);
        authorize(order, token); return order;
    }
    public Response cancel(String token, UUID id) {
        OrderView order = order(token, id);
        if (!"CANCELLED".equals(order.status())) transactions.freezeCancellation(id);
        coordinator.cancellation(id);
        OrderView result = transactions.order(id);
        return Response.status("PENDING_CANCEL".equals(result.status()) ? 202 : 200).entity(result).build();
    }
    private void authorize(OrderView order, String token) {
        OwnerContext context = context(new ContextRequest(OwnerType.valueOf(order.ownerType()), order.ownerId(), null), token);
        if (!order.ownerId().equals(context.ownerId()) || !order.ownerType().equals(context.ownerType().name()))
            throw new MarketException(403, "The current player cannot manage this order.");
    }
    private OwnerContext context(ContextRequest request, String token) {
        try { return core.context(request, token); }
        catch (AssetsFailure failure) { throw new MarketException(failure.status() >= 500 ? 503 : failure.status(),
                failure.status() >= 500 ? "Market authorization is temporarily unavailable." : failure.getMessage()); }
    }
    private void requireV7(UUID id) {
        if (id == null || id.version() != 7 || id.variant() != 2) throw new MarketException(400, "IDs must be RFC 9562 UUIDv7 values.");
    }
}
