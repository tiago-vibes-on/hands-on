package io.tiagovibeson.heroassociation.application.expedition;

import java.time.Instant;
import java.util.*;
import io.tiagovibeson.heroassociation.application.assets.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ExpeditionAdmissionService {
    @Inject ExpeditionAdmissionTransactions transactions;
    @Inject AssetsClient assets;
    @Inject ObjectMapper mapper;
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public ExpeditionBaseline reserve(UUID expedition, UUID manager, UUID agency, UUID party) {
        transactions.reserve(expedition, manager, agency, party);
        var claim = transactions.claim(expedition);
        if (claim.confirmed()) return claim.baseline();
        var command = AssetCommands.base(mapper, claim.commandKey(), "HERO_LOADOUT_SNAPSHOT", manager);
        command.set("heroIds", mapper.valueToTree(claim.baseline().heroes().stream().map(ExpeditionBaseline.Hero::heroId).toList()));
        var receipt = assets.execute(command); AssetCommands.validateReceipt(command, receipt);
        if (!"APPLIED".equals(receipt.path("status").asText())) throw AssetsClient.unavailable("Assets could not confirm the admission loadout.");
        return transactions.complete(expedition, receipt);
    }
    public List<ReservationCandidate> orphanCandidates(Instant olderThan, int limit) {
        return transactions.orphanCandidates(olderThan, limit).stream().map(value -> new ReservationCandidate(value.expeditionId(), value.ownerManagerId())).toList();
    }
    public boolean releaseProvenAbsent(UUID expedition, UUID manager) { return transactions.releaseProvenAbsent(expedition, manager); }
    public record ReservationCandidate(UUID expeditionId, UUID ownerManagerId) { }
}
