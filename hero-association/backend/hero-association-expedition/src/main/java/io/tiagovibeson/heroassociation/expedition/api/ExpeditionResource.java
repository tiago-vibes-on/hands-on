package io.tiagovibeson.heroassociation.expedition.api;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.tiagovibeson.heroassociation.expedition.CoreAdmissionClient.CoreAdmissionException;
import io.tiagovibeson.heroassociation.expedition.ExpeditionPlayerService;
import io.tiagovibeson.heroassociation.expedition.ExpeditionService.RunConflictException;
import io.tiagovibeson.heroassociation.expedition.ExpeditionService.RunNotFoundException;
import io.tiagovibeson.heroassociation.expedition.ExpeditionView;
import io.tiagovibeson.heroassociation.expedition.RedisFightTimelineStore;
import io.tiagovibeson.heroassociation.expedition.RunState;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Dormant until the BFF/WebSocket Map flow is ready for player traffic. */
@Path("/api/v1/expeditions")
@Authenticated
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ExpeditionResource {

    @Inject ExpeditionPlayerService players;
    @Inject RedisFightTimelineStore timelines;
    @Inject SecurityIdentity identity;

    @ConfigProperty(name = "expedition.player-api.enabled")
    boolean enabled;

    @POST
    public Response start(StartRequest request) {
        if (!enabled) return disabled();
        String token = accessToken();
        return execute(() -> {
            if (request == null) throw new IllegalArgumentException("Start request is required.");
            requireUuidV7(request.expeditionId(), "expeditionId");
            requireUuidV7(request.agencyId(), "agencyId");
            requireUuidV7(request.partyId(), "partyId");
            requireUuidV7(request.mapId(), "mapId");
            requireUuidV7(request.commandId(), "commandId");
            return players.start(request.expeditionId(), request.agencyId(), request.partyId(),
                    request.mapId(), request.commandId(), token);
        }, request == null ? null : request.expeditionId(), token, Response.Status.CREATED);
    }

    @GET
    @Path("/active")
    public Response active() {
        if (!enabled) return disabled();
        String token = accessToken();
        try {
            RunState run = players.active(token);
            return run == null ? Response.noContent().build() : Response.ok(view(run)).build();
        } catch (RuntimeException failure) {
            return error(failure, null, token);
        }
    }

    @GET
    @Path("/{expeditionId}")
    public Response get(@PathParam("expeditionId") UUID expeditionId) {
        if (!enabled) return disabled();
        String token = accessToken();
        return execute(() -> {
            requireUuidV7(expeditionId, "expeditionId");
            return players.get(expeditionId, token);
        }, expeditionId, token, Response.Status.OK);
    }

    @POST
    @Path("/{expeditionId}/continue")
    public Response continueRun(@PathParam("expeditionId") UUID expeditionId, VersionedCommand command) {
        if (!enabled) return disabled();
        String token = accessToken();
        return execute(() -> {
            validateCommand(expeditionId, command);
            return players.continueRun(expeditionId, command.commandId(), command.expectedVersion(), token);
        }, expeditionId, token, Response.Status.OK);
    }

    @POST
    @Path("/{expeditionId}/return")
    public Response returnRun(@PathParam("expeditionId") UUID expeditionId, VersionedCommand command) {
        if (!enabled) return disabled();
        String token = accessToken();
        return execute(() -> {
            validateCommand(expeditionId, command);
            return players.returnRun(expeditionId, command.commandId(), command.expectedVersion(), token);
        }, expeditionId, token, Response.Status.OK);
    }

    private Response execute(Supplier<RunState> action, UUID expeditionId, String token,
                             Response.Status successStatus) {
        try {
            return Response.status(successStatus).entity(view(action.get())).build();
        } catch (RuntimeException failure) {
            return error(failure, expeditionId, token);
        }
    }

    private Response error(RuntimeException failure, UUID expeditionId, String token) {
        if (failure instanceof RunConflictException) {
            ExpeditionView current = null;
            if (expeditionId != null) {
                try {
                    current = view(players.get(expeditionId, token));
                } catch (RuntimeException ignored) {
                    // A different active run or a failed Core lookup must not disclose another run.
                }
            }
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorView(failure.getMessage(), current)).build();
        }
        if (failure instanceof RunNotFoundException) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorView("Expedition not found.", null)).build();
        }
        if (failure instanceof IllegalArgumentException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorView(failure.getMessage(), null)).build();
        }
        if (failure instanceof CoreAdmissionException coreFailure && coreFailure.status() >= 400
                && coreFailure.status() < 500) {
            return Response.status(coreFailure.status())
                    .entity(new ErrorView("Core rejected the Expedition command.", null)).build();
        }
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new ErrorView("Expedition is temporarily unavailable.", null)).build();
    }

    private ExpeditionView view(RunState run) {
        return ExpeditionView.from(run, Instant.now(), timelines.get(run));
    }

    private String accessToken() {
        AccessTokenCredential credential = identity.getCredential(AccessTokenCredential.class);
        if (credential == null || credential.getToken() == null) {
            throw new NotAuthorizedException("Bearer");
        }
        return credential.getToken();
    }

    private Response disabled() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorView("Expedition player API is not enabled.", null)).build();
    }

    private void validateCommand(UUID expeditionId, VersionedCommand command) {
        requireUuidV7(expeditionId, "expeditionId");
        if (command == null || command.expectedVersion() < 1) {
            throw new IllegalArgumentException("A positive expectedVersion is required.");
        }
        requireUuidV7(command.commandId(), "commandId");
    }

    private void requireUuidV7(UUID value, String field) {
        if (value == null || value.version() != 7) {
            throw new IllegalArgumentException(field + " must be UUIDv7.");
        }
    }

    public record StartRequest(UUID expeditionId, UUID agencyId, UUID partyId,
                               UUID mapId, UUID commandId) { }
    public record VersionedCommand(UUID commandId, long expectedVersion) { }
    public record ErrorView(String message, ExpeditionView current) { }
}
