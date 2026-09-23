package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.PartyNameAlreadyUsedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class PartyNameAlreadyUsedExceptionMapper implements ExceptionMapper<PartyNameAlreadyUsedException> {

    @Override
    public Response toResponse(PartyNameAlreadyUsedException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
