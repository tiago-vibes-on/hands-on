package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.PartyNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class PartyNotFoundExceptionMapper implements ExceptionMapper<PartyNotFoundException> {

    @Override
    public Response toResponse(PartyNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
