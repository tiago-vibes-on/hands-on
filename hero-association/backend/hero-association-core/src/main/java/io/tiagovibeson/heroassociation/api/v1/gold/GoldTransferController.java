package io.tiagovibeson.heroassociation.api.v1.gold;

import io.tiagovibeson.heroassociation.application.GoldTransferService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/gold-transfers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GoldTransferController {

    @Inject
    GoldTransferService goldTransferService;

    @POST
    public GoldTransferResponse transfer(GoldTransferRequest request) {
        return goldTransferService.transfer(request);
    }
}
