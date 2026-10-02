package io.tiagovibeson.heroassociation.assets.application;

import java.math.BigDecimal;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import io.tiagovibeson.heroassociation.assets.domain.*;
import io.tiagovibeson.heroassociation.assets.application.exception.AssetOperationRejectedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class GoldTransfers {
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @Inject AssetCommandLocks keys;
    @Inject AssetBalances balances;
    @Inject CoreAuthorityClient authority;
    @Inject GoldTransfers transactions;

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public TransferResponse transfer(String subject, String token, TransferRequest request) {
        if (request == null || request.direction() == null || request.amountGold() == null)
            throw new AssetOperationRejectedException(400, "A direction and positive whole gold amount are required.");
        UUID key = request.operationKey();
        if (key == null || key.version() != 7 || key.variant() != 2) throw new AssetOperationRejectedException(400, "Operation key must be RFC 9562 UUIDv7.");
        long amount;
        try { amount = request.amountGold().longValueExact(); }
        catch (ArithmeticException invalid) { throw new AssetOperationRejectedException(400, "Gold amount must be a supported whole number."); }
        if (amount <= 0) throw new AssetOperationRejectedException(400, "Gold amount must be positive.");
        String agency = normalized(request.agencyName());
        String manager = request.direction() == GoldTransferDirection.AGENCY_TO_MANAGER ? normalized(request.managerName()) : null;
        if (manager == null && request.managerName() != null && !request.managerName().isBlank())
            throw new AssetOperationRejectedException(400, "A deposit must not specify a recipient Manager.");
        Intent intent = new Intent(key, subject, request.direction(), agency, manager, amount);
        TransferResponse replay = transactions.replay(intent);
        if (replay != null) return replay;
        var context = authority.transfer(intent.direction().name(), agency, manager, token);
        return transactions.apply(intent, context);
    }
    @Transactional public TransferResponse replay(Intent intent) {
        AssetCommandReceipt receipt = em.find(AssetCommandReceipt.class, intent.operationKey());
        return receipt == null ? null : read(receipt, intent);
    }
    @Transactional public TransferResponse apply(Intent intent, CoreAuthorityClient.TransferContext context) {
        keys.lock(intent.operationKey());
        AssetCommandReceipt replay = em.find(AssetCommandReceipt.class, intent.operationKey());
        if (replay != null) return read(replay, intent);
        if (context == null || context.requesterManagerId() == null || context.managerId() == null || context.agencyId() == null || context.managerName() == null || context.agencyName() == null
                || intent.direction() == GoldTransferDirection.MANAGER_TO_AGENCY && !context.requesterManagerId().equals(context.managerId()))
            throw new AssetOperationRejectedException(502, "Core returned an inconsistent transfer context.");
        balances.lockOwners(List.of(context.managerId(), context.agencyId()));
        AssetWallet manager = balances.wallet(AssetOwnerType.MANAGER, context.managerId());
        AssetWallet agency = balances.wallet(AssetOwnerType.AGENCY, context.agencyId());
        AssetWallet source = intent.direction() == GoldTransferDirection.MANAGER_TO_AGENCY ? manager : agency;
        AssetWallet target = source == manager ? agency : manager;
        if (source.gold < intent.amountGold()) throw new AssetOperationRejectedException("The source owner does not have enough gold.");
        if (target.gold > Long.MAX_VALUE - intent.amountGold()) throw new AssetOperationRejectedException("The recipient gold balance would overflow.");
        balances.gold(source, -intent.amountGold(), intent.operationKey());
        balances.gold(target, intent.amountGold(), intent.operationKey());
        TransferResponse response = new TransferResponse(intent.operationKey(), intent.direction(), intent.amountGold(),
                agency.ownerId, context.agencyName(), agency.gold, manager.ownerId, context.managerName(), manager.gold);
        em.persist(new AssetCommandReceipt(intent.operationKey(), "GOLD_TRANSFER", encode(intent), encode(response)));
        em.flush(); return response;
    }
    private TransferResponse read(AssetCommandReceipt receipt, Intent intent) {
        try {
            if (!"GOLD_TRANSFER".equals(receipt.kind) || !intent.equals(mapper.readValue(receipt.requestJson, Intent.class)))
                throw new AssetOperationRejectedException("Operation key was already used for a different transfer.");
            return mapper.readValue(receipt.responseJson, TransferResponse.class);
        } catch (java.io.IOException invalid) { throw new IllegalStateException("Stored transfer receipt is invalid.", invalid); }
    }
    private String normalized(String name) {
        if (name == null || name.isBlank()) throw new AssetOperationRejectedException(400, "Owner name is required.");
        return name.trim().toLowerCase(Locale.ROOT);
    }
    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); } catch (java.io.IOException invalid) { throw new IllegalStateException("Transfer receipt could not be encoded.", invalid); }
    }
    public record TransferRequest(UUID operationKey, GoldTransferDirection direction, String agencyName, String managerName, BigDecimal amountGold) { }
    public record TransferResponse(UUID operationKey, GoldTransferDirection direction, long amountGold, UUID agencyId, String agencyName,
                                   long agencyGold, UUID managerId, String managerName, long managerGold) { }
    public record Intent(UUID operationKey, String subject, GoldTransferDirection direction, String agencyName, String managerName, long amountGold) { }
}
