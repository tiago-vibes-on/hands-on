package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;
import io.tiagovibeson.heroassociation.application.AgencyAccessService;
import io.tiagovibeson.heroassociation.application.assets.AssetWorkflowTransactions;
import io.tiagovibeson.heroassociation.domain.AssetWorkflow;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/asset-operations") @Produces(MediaType.APPLICATION_JSON)
public class AssetWorkflowResource {
    @Inject AssetWorkflowTransactions transactions;
    @Inject AgencyAccessService access;
    @GET @Path("/{id}") public AssetWorkflow.View status(@PathParam("id") UUID id) {
        return transactions.view(id, access.currentManager().getId());
    }
}
