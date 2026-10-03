package io.tiagovibeson.heroassociation.api.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;
import io.quarkus.security.Authenticated;
import io.tiagovibeson.heroassociation.application.AgencyAccessService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/internal/v1/quest-authority") @Produces(MediaType.APPLICATION_JSON)
public class QuestAuthorityResource {
    @Inject AgencyAccessService access;
    @Inject EntityManager em;
    @ConfigProperty(name = "hero-association.quest.core-service-key") Optional<String> serviceKey;
    @GET @Path("/me") @Authenticated @Transactional
    public Identity current(@HeaderParam("X-Hero-Association-Quest-Core-Service-Key") String supplied) {
        String expected = serviceKey.orElse("");
        if (expected.length() < 32) throw new ServiceUnavailableException("Quest identity authority is not configured.");
        if (supplied == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) throw new ForbiddenException();
        UUID manager = access.currentManager().getId();
        long membership = em.createQuery("select count(m) from AgencyMember m where m.manager.id = :manager", Long.class).setParameter("manager", manager).getSingleResult();
        long away = em.createQuery("select count(r) from ExpeditionReservation r where r.ownerManagerId = :manager and r.appliedAt is null and r.releasedAt is null", Long.class)
                .setParameter("manager", manager).getSingleResult();
        return new Identity(manager, membership > 0 && away == 0);
    }
    public record Identity(UUID managerId, boolean atAgency) { }
}
