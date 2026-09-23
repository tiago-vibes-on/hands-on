package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.AgencyLeaderRequiredException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AgencyLeaderRequiredExceptionMapper implements ExceptionMapper<AgencyLeaderRequiredException> {

    @Override
    public Response toResponse(AgencyLeaderRequiredException exception) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
