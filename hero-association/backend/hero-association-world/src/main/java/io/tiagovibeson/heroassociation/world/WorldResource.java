package io.tiagovibeson.heroassociation.world;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import io.quarkus.security.Authenticated;
import io.tiagovibeson.heroassociation.contract.WorldContract.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class WorldResource {
    @Inject WorldCatalog catalog;
    @ConfigProperty(name = "hero-association.world.service-key") Optional<String> serviceKey;

    @GET @Path("api/v1/maps") @Authenticated
    public List<io.tiagovibeson.heroassociation.contract.WorldContract.MapDefinition> maps() { return catalog.maps(); }
    @GET @Path("api/v1/creatures") @Authenticated
    public List<Creature> creatures() { return catalog.creatures(); }
    @GET @Path("internal/v1/world/maps/{id}")
    public Plan latest(@HeaderParam("X-Hero-Association-World-Service-Key") String key, @PathParam("id") UUID id) {
        authenticate(key); return catalog.plan(id, null);
    }
    @GET @Path("internal/v1/world/maps/{id}/{version}")
    public Plan version(@HeaderParam("X-Hero-Association-World-Service-Key") String key, @PathParam("id") UUID id, @PathParam("version") int version) {
        authenticate(key); return catalog.plan(id, version);
    }
    private void authenticate(String supplied) {
        String expected = serviceKey.orElse("");
        if (expected.length() < 32) throw new ServiceUnavailableException("World service credential is not configured.");
        if (supplied == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) throw new ForbiddenException();
    }
}
