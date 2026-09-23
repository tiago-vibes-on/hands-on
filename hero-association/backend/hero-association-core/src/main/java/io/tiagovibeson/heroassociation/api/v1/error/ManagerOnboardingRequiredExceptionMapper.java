package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.ManagerOnboardingRequiredException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ManagerOnboardingRequiredExceptionMapper implements ExceptionMapper<ManagerOnboardingRequiredException> {

    @Override
    public Response toResponse(ManagerOnboardingRequiredException exception) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
