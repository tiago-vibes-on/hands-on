package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.expedition.ReservationClosedException;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Provider
public class ReservationClosedExceptionMapper implements ExceptionMapper<ReservationClosedException> {
    public Response toResponse(ReservationClosedException failure) {
        return Response.status(409).entity(Map.of("message", failure.getMessage())).build();
    }
}
