package io.tiagovibeson.heroassociation.repository;

import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.AssetReservation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class AssetReservationRepository implements PanacheRepositoryBase<AssetReservation, UUID> {

    public Optional<AssetReservation> findByKey(UUID reservationKey) {
        return find("reservationKey", reservationKey).firstResultOptional();
    }

    public Optional<AssetReservation> findByKeyForUpdate(UUID reservationKey) {
        return find("reservationKey", reservationKey)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }
}
