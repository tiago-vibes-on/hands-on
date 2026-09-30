package io.tiagovibeson.heroassociation.domain.combat;

public record CombatHit(String targetId, int damage, boolean critical, boolean defeated) {
}
