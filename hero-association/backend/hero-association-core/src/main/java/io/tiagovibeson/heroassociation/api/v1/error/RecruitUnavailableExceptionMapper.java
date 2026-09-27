package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.RecruitUnavailableException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class RecruitUnavailableExceptionMapper implements ExceptionMapper<RecruitUnavailableException> {

    @Override
    public Response toResponse(RecruitUnavailableException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
