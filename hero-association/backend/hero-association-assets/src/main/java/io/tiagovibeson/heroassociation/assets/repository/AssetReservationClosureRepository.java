package io.tiagovibeson.heroassociation.assets.repository;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.assets.domain.AssetReservationClosure;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AssetReservationClosureRepository implements PanacheRepositoryBase<AssetReservationClosure, UUID> {
    public boolean exists(UUID reservationKey) {
        return count("reservationKey", reservationKey) != 0;
    }
}
