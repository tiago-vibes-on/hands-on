package io.tiagovibeson.heroassociation.market;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

public class MarketException extends WebApplicationException {
    public MarketException(int status, String message) {
        super(message, Response.status(status).type(MediaType.APPLICATION_JSON).entity(Map.of("message", message)).build());
    }
}
