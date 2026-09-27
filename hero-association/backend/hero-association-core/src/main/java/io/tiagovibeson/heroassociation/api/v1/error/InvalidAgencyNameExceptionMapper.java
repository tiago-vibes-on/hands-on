package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.InvalidAgencyNameException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidAgencyNameExceptionMapper implements ExceptionMapper<InvalidAgencyNameException> {

    @Override
    public Response toResponse(InvalidAgencyNameException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
