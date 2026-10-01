package io.tiagovibeson.heroassociation.repository;

import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.AssetOperationReceipt;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AssetOperationReceiptRepository implements PanacheRepositoryBase<AssetOperationReceipt, UUID> {

    public Optional<AssetOperationReceipt> findByKey(UUID operationKey) {
        return find("operationKey", operationKey).firstResultOptional();
    }
}
