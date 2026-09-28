package io.tiagovibeson.heroassociation.api.v1.market;

import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.application.MarketOrderService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/market/orders")
@Produces(MediaType.APPLICATION_JSON)
public class MarketController {

    @Inject
    MarketOrderService marketOrderService;

    @GET
    public List<MarketOrderResponse> listOrders() {
        return marketOrderService.listOpenOrders();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public MarketOrderResponse createOrder(@NotNull @Valid CreateMarketOrderRequest request) {
        return marketOrderService.createOrder(
                request.agencyId(),
                request.side(),
                request.itemId(),
                request.quantity(),
                request.priceGoldPerItem());
    }

    @DELETE
    @Path("/{orderId}")
    public MarketOrderResponse cancelOrder(@PathParam("orderId") UUID orderId) {
        return marketOrderService.cancelOrder(orderId);
    }
}
