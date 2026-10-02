package io.tiagovibeson.heroassociation.application.assets;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.tiagovibeson.heroassociation.application.AgencyAccessService;
import io.tiagovibeson.heroassociation.application.AgencyStateService;
import io.tiagovibeson.heroassociation.domain.AssetWorkflow;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AssetWorkflows {
    private static final Logger LOG = Logger.getLogger(AssetWorkflows.class);
    @Inject AssetWorkflowTransactions transactions;
    @Inject AssetsClient assets;
    @Inject ObjectMapper mapper;
    @Inject AgencyStateService state;
    @Inject AgencyAccessService access;
    @Inject MeterRegistry metrics;

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void recover(UUID id) {
        var claim = transactions.claim(id); if (claim == null) return;
        metrics.counter("core.assets.recovery.attempts").increment();
        try {
            var command = mapper.readTree(claim.commandJson());
            var receipt = assets.execute(command);
            transactions.finish(claim, receipt);
        } catch (AssetCommands.ProtocolConflict conflict) {
            transactions.retry(claim, true, conflict.getMessage());
            metrics.counter("core.assets.recovery.failures", "category", "conflict").increment();
            LOG.errorf("Asset workflow %s is quarantined: %s", id, conflict.getMessage());
        } catch (AssetsClient.AssetsFailure failure) {
            boolean conflict = failure.status == 400 || failure.status == 404 || failure.status == 409 || failure.status == 422;
            transactions.retry(claim, conflict, conflict ? "Assets rejected the staged command protocol." : "Assets is temporarily unavailable.");
            metrics.counter("core.assets.recovery.failures", "category", conflict ? "conflict" : "unavailable").increment();
        } catch (Exception unavailable) {
            transactions.retry(claim, false, "Asset operation awaits confirmation.");
            metrics.counter("core.assets.recovery.failures", "category", "retry").increment();
        }
    }
    public Response response(UUID agencyId, UUID id) {
        recover(id);
        AssetWorkflow.View view = transactions.view(id, access.currentManager().getId());
        if ("APPLIED".equals(view.status())) return Response.ok(state.findState(agencyId)).build();
        if ("REJECTED".equals(view.status()) || "CONFLICT".equals(view.status())) throw new WebApplicationException(Response.status(view.rejectionStatus()).entity(view).build());
        return Response.accepted(view).build();
    }
    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void poll() {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(20).toNanos();
        for (UUID id : transactions.due()) { if (System.nanoTime() >= deadline) break; recover(id); }
    }
    @jakarta.annotation.PostConstruct void registerMetric() {
        metrics.gauge("core.assets.pending.operations", transactions, AssetWorkflowTransactions::pendingCount);
    }
}
