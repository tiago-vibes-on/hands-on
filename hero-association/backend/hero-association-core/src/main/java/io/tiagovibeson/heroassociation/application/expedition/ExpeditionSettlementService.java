package io.tiagovibeson.heroassociation.application.expedition;

import io.tiagovibeson.heroassociation.application.assets.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ExpeditionSettlementService {
    @Inject ExpeditionSettlementTransactions transactions;
    @Inject AssetWorkflows workflows;
    @Inject AssetWorkflowTransactions states;
    public enum Result { APPLIED, DUPLICATE }
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public Result apply(byte[] body, String messageId, String contentType) {
        var stage = transactions.stage(body, messageId, contentType);
        if (stage.duplicate()) return Result.DUPLICATE;
        workflows.recover(stage.operationKey());
        var state = states.view(stage.operationKey(), null);
        if (!"APPLIED".equals(state.status())) throw AssetsClient.unavailable("Expedition asset credit awaits confirmation.");
        return Result.APPLIED;
    }
}
