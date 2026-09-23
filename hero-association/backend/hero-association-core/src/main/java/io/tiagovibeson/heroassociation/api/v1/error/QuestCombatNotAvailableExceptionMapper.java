package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.QuestCombatNotAvailableException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class QuestCombatNotAvailableExceptionMapper implements ExceptionMapper<QuestCombatNotAvailableException> {

    @Override
    public Response toResponse(QuestCombatNotAvailableException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
