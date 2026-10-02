package io.tiagovibeson.heroassociation.application;

import java.util.UUID;
import io.tiagovibeson.heroassociation.application.assets.*;
import io.tiagovibeson.heroassociation.domain.RuneInventoryOwnerType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class RuneLoadoutService {
    @Inject AssetWorkflowTransactions transactions;
    @Inject AssetWorkflows workflows;
    public Response equip(UUID agency, UUID hero, int slot, UUID rune, RuneInventoryOwnerType source, UUID operationKey) {
        return workflows.response(agency, transactions.rune(operationKey, agency, hero, slot, rune, source, true));
    }
    public Response unequip(UUID agency, UUID hero, int slot, UUID operationKey) {
        return workflows.response(agency, transactions.rune(operationKey, agency, hero, slot, null, null, false));
    }
}
