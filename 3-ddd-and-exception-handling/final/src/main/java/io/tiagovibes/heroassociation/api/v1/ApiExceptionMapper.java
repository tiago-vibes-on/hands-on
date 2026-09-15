package io.tiagovibes.heroassociation.api.v1;

import io.tiagovibes.heroassociation.application.HeroApplicationException;
import io.tiagovibes.heroassociation.application.HeroNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<HeroApplicationException> {

    @Override
    public Response toResponse(HeroApplicationException exception) {
        Response.Status status = exception instanceof HeroNotFoundException
                ? Response.Status.NOT_FOUND
                : Response.Status.CONFLICT;

        return Response.status(status)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
