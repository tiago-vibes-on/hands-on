package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.MarketOrderOwnerRequiredException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class MarketOrderOwnerRequiredExceptionMapper implements ExceptionMapper<MarketOrderOwnerRequiredException> {

    @Override
    public Response toResponse(MarketOrderOwnerRequiredException exception) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
