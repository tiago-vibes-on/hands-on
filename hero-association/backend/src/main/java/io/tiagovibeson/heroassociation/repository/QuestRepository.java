package io.tiagovibeson.heroassociation.repository;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.tiagovibeson.heroassociation.domain.Quest;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QuestRepository implements PanacheRepositoryBase<Quest, UUID> {
}
