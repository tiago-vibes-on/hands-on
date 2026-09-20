package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.HeroApplicationException;
import io.tiagovibeson.heroassociation.application.exception.HeroNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.InvalidHeroUpdateException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<HeroApplicationException> {

    @Override
    public Response toResponse(HeroApplicationException exception) {
        Response.Status status = switch (exception) {
            case HeroNotFoundException _ -> Response.Status.NOT_FOUND;
            case InvalidHeroUpdateException _ -> Response.Status.BAD_REQUEST;
            default -> Response.Status.CONFLICT;
        };

        return Response.status(status)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
