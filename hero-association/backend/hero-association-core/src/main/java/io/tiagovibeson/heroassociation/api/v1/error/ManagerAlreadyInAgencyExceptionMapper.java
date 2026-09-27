package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.ManagerAlreadyInAgencyException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ManagerAlreadyInAgencyExceptionMapper implements ExceptionMapper<ManagerAlreadyInAgencyException> {

    @Override
    public Response toResponse(ManagerAlreadyInAgencyException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
