package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.AgencyMembershipRequiredException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AgencyMembershipRequiredExceptionMapper implements ExceptionMapper<AgencyMembershipRequiredException> {

    @Override
    public Response toResponse(AgencyMembershipRequiredException exception) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
