package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.HeroOnQuestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class HeroOnQuestExceptionMapper implements ExceptionMapper<HeroOnQuestException> {

    @Override
    public Response toResponse(HeroOnQuestException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
