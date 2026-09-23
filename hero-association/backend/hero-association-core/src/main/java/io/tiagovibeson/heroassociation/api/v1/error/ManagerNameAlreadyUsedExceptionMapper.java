package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.ManagerNameAlreadyUsedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ManagerNameAlreadyUsedExceptionMapper implements ExceptionMapper<ManagerNameAlreadyUsedException> {

    @Override
    public Response toResponse(ManagerNameAlreadyUsedException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
