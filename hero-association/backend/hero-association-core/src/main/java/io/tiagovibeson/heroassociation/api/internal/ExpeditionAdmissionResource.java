package io.tiagovibeson.heroassociation.api.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.security.Authenticated;
import io.tiagovibeson.heroassociation.application.AgencyAccessService;
import io.tiagovibeson.heroassociation.application.expedition.ExpeditionAdmissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;

/** Internal Expedition-to-Core boundary; never routed through the browser BFF. */
@Path("/internal/v1/expedition-admissions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ExpeditionAdmissionResource {

    public static final UUID FIRST_FIELD_ID = UUID.fromString("019c4c00-0006-7000-8000-000000000001");

    @Inject ExpeditionAdmissionService admission;
    @Inject AgencyAccessService access;

    @ConfigProperty(name = "hero-association.expedition.core-service-key")
    Optional<String> serviceKey;

    @POST
    @Path("/reservations")
    @Authenticated
    public ExpeditionAdmissionResponse reserve(@HeaderParam("X-Hero-Association-Service-Key") String key,
                                                ReserveRequest request) {
        requireServiceKey(key);
        if (request == null) throw new IllegalArgumentException("Admission request is required.");
        io.tiagovibeson.heroassociation.contract.WorldContract.id(request.mapId());
        UUID managerId = access.currentManager().getId();
        var world = admission.pinWorld(request.expeditionId(), managerId, request.agencyId(), request.partyId(), request.mapId());
        var baseline = admission.reserve(request.expeditionId(), managerId,
                request.agencyId(), request.partyId());
        return new ExpeditionAdmissionResponse(request.expeditionId(), managerId,
                request.agencyId(), request.partyId(), request.mapId(), world.map().version(), baseline, world);
    }

    @GET
    @Path("/me")
    @Authenticated
    public ManagerIdentityResponse currentManager(
            @HeaderParam("X-Hero-Association-Service-Key") String key) {
        requireServiceKey(key);
        return new ManagerIdentityResponse(access.currentManager().getId());
    }

    @GET
    @Path("/reservations/orphan-candidates")
    public List<ExpeditionAdmissionService.ReservationCandidate> orphanCandidates(
            @HeaderParam("X-Hero-Association-Service-Key") String key) {
        requireServiceKey(key);
        return admission.orphanCandidates(Instant.now().minus(Duration.ofMinutes(2)), 100);
    }

    @POST
    @Path("/reservations/{expeditionId}/release-proven-absent")
    public ReleaseResponse release(@HeaderParam("X-Hero-Association-Service-Key") String key,
                                   @PathParam("expeditionId") UUID expeditionId,
                                   ReleaseRequest request) {
        requireServiceKey(key);
        if (request == null) {
            throw new IllegalArgumentException("Manager ID is required.");
        }
        return new ReleaseResponse(admission.releaseProvenAbsent(expeditionId, request.managerId()));
    }

    private void requireServiceKey(String provided) {
        String expected = serviceKey.orElse(null);
        if (expected == null || expected.length() < 32) {
            throw new ServiceUnavailableException("Internal Expedition admission is not configured.");
        }
        if (provided == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException();
        }
    }

    public record ManagerIdentityResponse(UUID managerId) { }
    public record ReserveRequest(UUID expeditionId, UUID agencyId, UUID partyId, UUID mapId) { }
    public record ReleaseRequest(UUID managerId) { }
    public record ReleaseResponse(boolean released) { }
}
