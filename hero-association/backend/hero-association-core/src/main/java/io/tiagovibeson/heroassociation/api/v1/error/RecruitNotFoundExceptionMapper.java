package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.RecruitNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class RecruitNotFoundExceptionMapper implements ExceptionMapper<RecruitNotFoundException> {

    @Override
    public Response toResponse(RecruitNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
