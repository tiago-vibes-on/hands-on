package io.tiagovibeson.heroassociation.assets.api;

import io.tiagovibeson.heroassociation.assets.application.exception.AssetOperationRejectedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.*;

@Provider
public class AssetExceptionMapper implements ExceptionMapper<AssetOperationRejectedException> {
    public Response toResponse(AssetOperationRejectedException failure) {
        return Response.status(failure.status()).entity(java.util.Map.of("message", failure.getMessage())).build();
    }
}
