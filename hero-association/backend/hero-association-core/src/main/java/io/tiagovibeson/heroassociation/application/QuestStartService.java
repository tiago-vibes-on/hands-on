package io.tiagovibeson.heroassociation.application;

import java.util.UUID;
import io.tiagovibeson.heroassociation.application.assets.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class QuestStartService {
    @Inject AssetWorkflowTransactions transactions;
    @Inject AssetWorkflows workflows;
    public Response startQuest(UUID agency, UUID quest, UUID party, long expectedFee, UUID operationKey) {
        return workflows.response(agency, transactions.quest(operationKey, agency, quest, party, expectedFee));
    }
}
