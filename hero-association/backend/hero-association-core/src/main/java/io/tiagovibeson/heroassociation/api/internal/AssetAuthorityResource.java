package io.tiagovibeson.heroassociation.api.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import io.quarkus.security.Authenticated;
import io.tiagovibeson.heroassociation.application.AgencyAccessService;
import io.tiagovibeson.heroassociation.repository.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/** Core owns identity and agency permission; Assets owns the resulting economic mutation. */
@Path("/internal/v1/asset-authority") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON) @Authenticated
public class AssetAuthorityResource {
    @Inject AgencyAccessService access;
    @Inject AgencyRepository agencies;
    @Inject ManagerRepository managers;
    @ConfigProperty(name = "hero-association.assets.core-service-key") Optional<String> credential;
    @POST @Path("/context") @Transactional
    public OwnerContext context(@HeaderParam("X-Hero-Association-Assets-Core-Service-Key") String key, OwnerRequest request) {
        requireKey(key);
        if (request == null || request.ownerType() == null || !java.util.Set.of("MANAGER", "AGENCY").contains(request.ownerType())) throw new BadRequestException("Owner type is required.");
        var manager = access.currentManager();
        if ("MANAGER".equals(request.ownerType())) {
            if (request.ownerId() != null && !manager.getId().equals(request.ownerId())) throw new ForbiddenException();
            return new OwnerContext(manager.getId(), "MANAGER", manager.getId(), manager.getDisplayName());
        }
        requireId(request.ownerId()); access.requireLeadership(request.ownerId());
        var agency = agencies.findByIdOptional(request.ownerId()).orElseThrow(NotFoundException::new);
        return new OwnerContext(manager.getId(), "AGENCY", agency.getId(), agency.getName());
    }
    @POST @Path("/transfers") @Transactional
    public TransferContext transfer(@HeaderParam("X-Hero-Association-Assets-Core-Service-Key") String key, TransferRequest request) {
        requireKey(key);
        if (request == null || request.direction() == null) throw new BadRequestException("Direction is required.");
        var actor = access.currentManager();
        var agency = agencies.findByNormalizedName(normalize(request.agencyName())).orElseThrow(NotFoundException::new);
        if ("MANAGER_TO_AGENCY".equals(request.direction())) {
            if (request.managerName() != null) throw new BadRequestException("Deposit recipient must be omitted.");
            return new TransferContext(actor.getId(), actor.getId(), actor.getDisplayName(), agency.getId(), agency.getName());
        }
        if (!"AGENCY_TO_MANAGER".equals(request.direction())) throw new BadRequestException("Direction is invalid.");
        access.requireLeadership(agency.getId());
        var recipient = managers.findByNormalizedDisplayName(normalize(request.managerName())).orElseThrow(NotFoundException::new);
        return new TransferContext(actor.getId(), recipient.getId(), recipient.getDisplayName(), agency.getId(), agency.getName());
    }
    private String normalize(String name) {
        if (name == null || name.isBlank()) throw new BadRequestException("Owner name is required.");
        return name.trim().toLowerCase(Locale.ROOT);
    }
    private void requireId(UUID id) {
        if (id == null || id.version() != 7 || id.variant() != 2) throw new BadRequestException("Owner ID must be RFC 9562 UUIDv7.");
    }
    private void requireKey(String provided) {
        String expected = credential.orElse("");
        if (expected.length() < 32) throw new ServiceUnavailableException("Assets authority credentials are not configured.");
        if (provided == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) throw new ForbiddenException();
    }
    public record OwnerRequest(String ownerType, UUID ownerId) { }
    public record OwnerContext(UUID managerId, String ownerType, UUID ownerId, String ownerName) { }
    public record TransferRequest(String direction, String agencyName, String managerName) { }
    public record TransferContext(UUID requesterManagerId, UUID managerId, String managerName, UUID agencyId, String agencyName) { }
}
