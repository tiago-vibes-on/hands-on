package io.tiagovibeson.heroassociation.quest;

import java.util.Map;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.*;

@Provider
public class QuestExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
    public Response toResponse(IllegalArgumentException failure) {
        return Response.status(400).entity(Map.of("message", failure.getMessage())).build();
    }
}
