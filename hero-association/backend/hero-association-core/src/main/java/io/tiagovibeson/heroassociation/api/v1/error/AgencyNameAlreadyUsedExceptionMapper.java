package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.AgencyNameAlreadyUsedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AgencyNameAlreadyUsedExceptionMapper implements ExceptionMapper<AgencyNameAlreadyUsedException> {

    @Override
    public Response toResponse(AgencyNameAlreadyUsedException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
