package io.tiagovibeson.heroassociation.api.v1.market;

import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.MarketOrderService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/agencies/{agencyId}/market-orders")
@Produces(MediaType.APPLICATION_JSON)
public class MarketOrderController {

    @Inject
    MarketOrderService marketOrderService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public AgencyStateResponse createOrder(
            @PathParam("agencyId") UUID agencyId,
            @NotNull @Valid CreateMarketOrderRequest request) {
        return marketOrderService.createOrder(
                agencyId,
                request.side(),
                request.itemId(),
                request.quantity(),
                request.priceGoldPerItem());
    }

    @DELETE
    @Path("/{orderId}")
    public AgencyStateResponse cancelOrder(
            @PathParam("agencyId") UUID agencyId,
            @PathParam("orderId") UUID orderId) {
        return marketOrderService.cancelOrder(agencyId, orderId);
    }
}
