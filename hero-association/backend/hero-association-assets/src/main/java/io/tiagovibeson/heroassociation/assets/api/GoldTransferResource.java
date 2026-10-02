package io.tiagovibeson.heroassociation.assets.api;

import io.quarkus.security.Authenticated;
import io.tiagovibeson.heroassociation.assets.application.GoldTransfers;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/gold-transfers") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON) @Authenticated
public class GoldTransferResource {
    @Inject PlayerIdentity player;
    @Inject GoldTransfers transfers;
    @POST public GoldTransfers.TransferResponse transfer(GoldTransfers.TransferRequest request) {
        return transfers.transfer(player.subject(), player.token(), request);
    }
}
