package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.HeroAlreadyAssignedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class HeroAlreadyAssignedExceptionMapper implements ExceptionMapper<HeroAlreadyAssignedException> {

    @Override
    public Response toResponse(HeroAlreadyAssignedException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
