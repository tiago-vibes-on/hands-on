package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.InvalidHeroActivityException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidHeroActivityExceptionMapper implements ExceptionMapper<InvalidHeroActivityException> {

    @Override
    public Response toResponse(InvalidHeroActivityException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
