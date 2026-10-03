package io.tiagovibeson.heroassociation.quest;

import java.util.UUID;
import io.quarkus.scheduler.Scheduled;
import io.tiagovibeson.heroassociation.contract.QuestContract.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class QuestReturns {
    @Inject QuestTransactions transactions;
    @Inject QuestAssetsClient assets;
    public ReturnReceipt apply(ReturnRequest request) {
        var receipt = transactions.stage(request);
        if ("REWARD_PENDING".equals(receipt.status())) recover(request.expeditionId());
        return transactions.receipt(request.expeditionId());
    }
    public void recover(UUID expeditionId) {
        var claim = transactions.claim(expeditionId); if (claim == null) return;
        try { assets.reward(claim.assignment()); transactions.finish(claim); }
        catch (QuestAssetsClient.ProtocolConflict conflict) { transactions.retry(claim, true, conflict.getMessage()); }
        catch (RuntimeException unavailable) { transactions.retry(claim, false, "Quest reward awaits Assets confirmation."); }
    }
    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void retry() { transactions.due().forEach(this::recover); }
}
