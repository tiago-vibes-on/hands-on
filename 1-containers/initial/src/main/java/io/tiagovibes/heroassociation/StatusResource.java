package io.tiagovibes.heroassociation;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/status")
@Produces(MediaType.APPLICATION_JSON)
public class StatusResource {

    @GET
    public StatusResponse status() {
        return new StatusResponse("hero-association", "UP");
    }

    public record StatusResponse(String service, String status) {
    }
}
