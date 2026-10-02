package io.tiagovibeson.heroassociation.assets.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.security.Authenticated;
import io.tiagovibeson.heroassociation.assets.application.AssetsService;
import io.tiagovibeson.heroassociation.assets.application.AssetCommandLocks;
import io.tiagovibeson.heroassociation.assets.domain.AssetOperationKind;
import io.tiagovibeson.heroassociation.assets.domain.AssetOperationReceipt;
import io.tiagovibeson.heroassociation.assets.domain.AssetOwnerType;
import io.tiagovibeson.heroassociation.assets.domain.AssetReservation;
import io.tiagovibeson.heroassociation.assets.domain.AssetResourceType;
import io.tiagovibeson.heroassociation.assets.repository.AssetOperationReceiptRepository;
import io.tiagovibeson.heroassociation.assets.repository.AssetReservationClosureRepository;
import io.tiagovibeson.heroassociation.assets.repository.AssetReservationRepository;
import io.tiagovibeson.heroassociation.assets.repository.ItemRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;

/** Dedicated Market credential only; the BFF must never proxy this prefix. */
@Path("/internal/v1/assets")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AssetsResource {
    @Inject io.tiagovibeson.heroassociation.assets.application.CoreAuthorityClient authority;
    @Inject PlayerIdentity player;
    @Inject ServiceAuthentication authentication;
    @Inject io.tiagovibeson.heroassociation.assets.application.AssetSnapshots snapshots;
    @Inject AssetsService assets;
    @Inject AssetCommandLocks commandLocks;
    @Inject AssetReservationRepository reservations;
    @Inject AssetReservationClosureRepository closures;
    @Inject AssetOperationReceiptRepository receipts;
    @Inject ItemRepository items;
    @ConfigProperty(name = "hero-association.assets.market-service-key") Optional<String> serviceKey;

    @POST
    @Path("/context")
    @Authenticated
    public OwnerContext context(@HeaderParam("X-Hero-Association-Market-Service-Key") String key,
                                @NotNull @Valid ContextRequest request) {
        requireServiceKey(key);
        var owner = authority.owner(request.ownerType(), request.ownerId(), player.token());
        ItemDefinition item = null;
        if (request.itemId() != null) {
            var definition = snapshots.item(request.itemId());
            item = new ItemDefinition(definition.id(), definition.code(), definition.name(), definition.symbol());
        }
        return new OwnerContext(owner.managerId(), owner.ownerType(), owner.ownerId(), owner.ownerName(), item);
    }

    @POST
    @Path("/reservations")
    @Authenticated
    public ReservationResponse reserve(@HeaderParam("X-Hero-Association-Market-Service-Key") String key,
                                       @NotNull @Valid ReserveRequest request) {
        requireServiceKey(key);
        var owner = authority.owner(request.ownerType(), request.ownerId(), player.token());
        if (owner.ownerType() != request.ownerType() || !owner.ownerId().equals(request.ownerId())) throw new ForbiddenException();
        return ReservationResponse.from(assets.reserveAuthorized(request.reservationKey(), request.ownerType(), request.ownerId(),
                request.resourceType(), request.itemId(), request.quantity(), request.unitPriceGoldPerItem(), owner.managerId()), false);
    }

    @GET
    @Path("/reservations/{reservationKey}")
    @Transactional
    public ReservationResponse reservation(@HeaderParam("X-Hero-Association-Market-Service-Key") String key,
                                           @PathParam("reservationKey") UUID reservationKey) {
        requireServiceKey(key);
        requireKey(reservationKey);
        commandLocks.lock(reservationKey);
        boolean closed = closures.exists(reservationKey);
        AssetReservation reservation = reservations.findByKey(reservationKey).orElse(null);
        if (reservation == null) {
            if (!closed) throw new NotFoundException();
            return new ReservationResponse(reservationKey, null, null, null, null, null, 0, 0, 0, true);
        }
        return ReservationResponse.from(reservation, closed);
    }

    @POST
    @Path("/releases")
    public OperationResponse release(@HeaderParam("X-Hero-Association-Market-Service-Key") String key,
                                     @NotNull @Valid ReleaseRequest request) {
        requireServiceKey(key);
        return OperationResponse.from(assets.release(request.operationKey(), request.reservationKey(), request.quantity()));
    }

    @POST
    @Path("/settlements")
    public OperationResponse settle(@HeaderParam("X-Hero-Association-Market-Service-Key") String key,
                                    @NotNull @Valid SettlementRequest request) {
        requireServiceKey(key);
        return OperationResponse.from(assets.settleTrade(request.operationKey(), request.buyerReservationKey(),
                request.sellerReservationKey(), request.quantity(), request.executionPriceGoldPerItem()));
    }

    @POST
    @Path("/reservations/{reservationKey}/close")
    public OperationResponse close(@HeaderParam("X-Hero-Association-Market-Service-Key") String key,
                                   @PathParam("reservationKey") UUID reservationKey,
                                   @NotNull @Valid CloseRequest request) {
        requireServiceKey(key);
        return OperationResponse.from(assets.close(request.operationKey(), reservationKey));
    }

    @GET
    @Path("/operations/{operationKey}")
    public OperationResponse operation(@HeaderParam("X-Hero-Association-Market-Service-Key") String key,
                                       @PathParam("operationKey") UUID operationKey) {
        requireServiceKey(key);
        requireKey(operationKey);
        return OperationResponse.from(receipts.findByKey(operationKey).orElseThrow(NotFoundException::new));
    }

    private void requireKey(UUID key) {
        if (key.version() != 7 || key.variant() != 2) {
            throw new BadRequestException("Key must be an RFC 9562 UUIDv7.");
        }
    }

    private void requireServiceKey(String provided) { authentication.market(provided); }

    public record ReserveRequest(@NotNull UUID reservationKey, @NotNull AssetOwnerType ownerType,
            @NotNull UUID ownerId, @NotNull AssetResourceType resourceType, @NotNull UUID itemId,
            @Positive int quantity, @Positive long unitPriceGoldPerItem) { }
    public record ContextRequest(@NotNull AssetOwnerType ownerType, UUID ownerId, UUID itemId) { }
    public record ItemDefinition(UUID id, String code, String name, String symbol) { }
    public record OwnerContext(UUID managerId, AssetOwnerType ownerType, UUID ownerId, String ownerName,
                               ItemDefinition item) { }
    public record ReleaseRequest(@NotNull UUID operationKey, @NotNull UUID reservationKey, @Positive int quantity) { }
    public record CloseRequest(@NotNull UUID operationKey) { }
    public record SettlementRequest(@NotNull UUID operationKey, @NotNull UUID buyerReservationKey,
            @NotNull UUID sellerReservationKey, @Positive int quantity, @Positive long executionPriceGoldPerItem) { }

    public record ReservationResponse(UUID reservationKey, UUID requesterManagerId, AssetOwnerType ownerType,
            UUID ownerId, AssetResourceType resourceType, UUID itemId, int initialQuantity,
            int remainingQuantity, long unitPriceGoldPerItem, boolean closed) {
        static ReservationResponse from(AssetReservation value, boolean closed) {
            return new ReservationResponse(value.getReservationKey(), value.getRequesterManagerId(), value.getOwnerType(),
                    value.getOwnerId(), value.getResourceType(), value.getItemId(), value.getInitialQuantity(),
                    value.getRemainingQuantity(), value.getUnitPriceGoldPerItem(), closed);
        }
    }
    public record OperationResponse(UUID operationKey, AssetOperationKind kind, UUID firstReservationKey,
            UUID secondReservationKey, int quantity, long executionPriceGoldPerItem) {
        static OperationResponse from(AssetOperationReceipt value) {
            return new OperationResponse(value.getOperationKey(), value.getKind(), value.getFirstReservationKey(),
                    value.getSecondReservationKey(), value.getQuantity(), value.getExecutionPriceGoldPerItem());
        }
    }
}
