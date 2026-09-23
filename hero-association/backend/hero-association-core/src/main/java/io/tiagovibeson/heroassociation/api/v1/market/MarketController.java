package io.tiagovibeson.heroassociation.api.v1.market;

import java.util.List;

import io.tiagovibeson.heroassociation.application.MarketOrderService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
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
}
