package io.tiagovibeson.heroassociation.repository;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.AgencyItem;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AgencyItemRepository implements PanacheRepositoryBase<AgencyItem, UUID> {
}
