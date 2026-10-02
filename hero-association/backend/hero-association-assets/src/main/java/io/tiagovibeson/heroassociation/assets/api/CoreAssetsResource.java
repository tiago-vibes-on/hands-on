package io.tiagovibeson.heroassociation.assets.api;

import java.util.UUID;
import io.tiagovibeson.heroassociation.assets.application.*;
import io.tiagovibeson.heroassociation.assets.application.AssetSnapshots.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/internal/v1/assets/core") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
public class CoreAssetsResource {
    @Inject ServiceAuthentication authentication;
    @Inject AssetSnapshots snapshots;
    @Inject CoreAssetCommands commands;
    @POST @Path("/snapshots") public Snapshot snapshot(@HeaderParam("X-Hero-Association-Assets-Core-Service-Key") String key, SnapshotRequest request) {
        authentication.core(key); return snapshots.read(request);
    }
    @POST @Path("/commands") public CoreAssetCommands.CommandReceipt execute(@HeaderParam("X-Hero-Association-Assets-Core-Service-Key") String key, CoreAssetCommands.Command request) {
        authentication.core(key); return commands.execute(request);
    }
    @GET @Path("/commands/{id}") public CoreAssetCommands.CommandReceipt receipt(@HeaderParam("X-Hero-Association-Assets-Core-Service-Key") String key, @PathParam("id") UUID id) {
        authentication.core(key); return commands.receipt(id);
    }
}
