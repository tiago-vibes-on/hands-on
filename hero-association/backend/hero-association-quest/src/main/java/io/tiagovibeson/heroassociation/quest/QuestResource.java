package io.tiagovibeson.heroassociation.quest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.tiagovibeson.heroassociation.contract.QuestContract.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class QuestResource {
    @Inject QuestTransactions transactions;
    @Inject QuestReturns returns;
    @Inject QuestAuthorityClient authority;
    @Inject SecurityIdentity identity;
    @ConfigProperty(name = "hero-association.quest.core-service-key") Optional<String> coreKey;
    @ConfigProperty(name = "hero-association.quest.expedition-service-key") Optional<String> expeditionKey;
    @ConfigProperty(name = "hero-association.quest.test-access-token") Optional<String> testToken;

    @GET @Path("api/v1/quests") @Authenticated
    public QuestTransactions.Board board() {
        var actor = authority.current(token()); return transactions.board(actor.managerId(), actor.atAgency());
    }
    @POST @Path("api/v1/quests/{definitionId}/accept") @Authenticated
    public Assignment accept(@PathParam("definitionId") UUID definitionId, Command command) {
        if (command == null) throw new BadRequestException("Quest command is required.");
        var actor = authority.current(token()); return transactions.accept(command.commandId(), actor.managerId(), definitionId, actor.atAgency());
    }
    @POST @Path("api/v1/quests/assignments/{assignmentId}/cancel") @Authenticated
    public Assignment cancel(@PathParam("assignmentId") UUID assignmentId, Command command) {
        if (command == null) throw new BadRequestException("Quest command is required.");
        var actor = authority.current(token()); return transactions.cancel(command.commandId(), actor.managerId(), assignmentId, actor.atAgency());
    }
    @POST @Path("internal/v1/quests/admissions")
    public Pin pin(@HeaderParam("X-Hero-Association-Quest-Expedition-Service-Key") String key, PinRequest request) {
        authenticate(expeditionKey, key); if (request == null) throw new BadRequestException("Quest pin request is required."); return transactions.pin(request);
    }
    @GET @Path("internal/v1/quests/admissions/orphan-candidates")
    public java.util.List<QuestTransactions.Orphan> orphans(@HeaderParam("X-Hero-Association-Quest-Expedition-Service-Key") String key) {
        authenticate(expeditionKey, key); return transactions.orphans();
    }
    @POST @Path("internal/v1/quests/admissions/{id}/release-proven-absent")
    public ReleaseResponse release(@HeaderParam("X-Hero-Association-Quest-Expedition-Service-Key") String key,
                                   @PathParam("id") UUID id, ReleaseRequest request) {
        authenticate(expeditionKey, key); if (request == null) throw new BadRequestException("Release request is required.");
        return new ReleaseResponse(transactions.release(id, request.managerId()));
    }
    @POST @Path("internal/v1/quests/returns")
    public ReturnReceipt apply(@HeaderParam("X-Hero-Association-Quest-Core-Service-Key") String key, ReturnRequest request) {
        authenticate(coreKey, key); if (request == null) throw new BadRequestException("Quest return is required."); return returns.apply(request);
    }
    private void authenticate(Optional<String> configured, String supplied) {
        String expected = configured.orElse("");
        if (expected.length() < 32) throw new ServiceUnavailableException("Quest service credentials are not configured.");
        if (supplied == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) throw new ForbiddenException();
    }
    private String token() {
        var credential = identity.getCredential(AccessTokenCredential.class);
        if (credential != null) return credential.getToken();
        return LaunchMode.current() == LaunchMode.TEST ? testToken.orElse(null) : null;
    }
    public record Command(UUID commandId) { }
    public record ReleaseRequest(UUID managerId) { }
    public record ReleaseResponse(boolean released) { }
}
