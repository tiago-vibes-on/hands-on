package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.InvalidQuestPartySizeException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidQuestPartySizeExceptionMapper implements ExceptionMapper<InvalidQuestPartySizeException> {

    @Override
    public Response toResponse(InvalidQuestPartySizeException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
