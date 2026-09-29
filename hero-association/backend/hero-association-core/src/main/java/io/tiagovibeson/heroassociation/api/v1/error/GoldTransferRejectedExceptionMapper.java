package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.GoldTransferRejectedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GoldTransferRejectedExceptionMapper implements ExceptionMapper<GoldTransferRejectedException> {

    @Override
    public Response toResponse(GoldTransferRejectedException exception) {
        return Response.status(exception.status())
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
