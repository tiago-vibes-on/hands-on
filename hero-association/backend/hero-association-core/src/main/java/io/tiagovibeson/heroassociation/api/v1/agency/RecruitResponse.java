package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.Hero;

public record RecruitResponse(
        UUID id,
        String name,
        String alias,
        String heroClass,
        int level,
        int health,
        int mana,
        int healthRecoveryPerSecond,
        int manaRecoveryPerSecond) {

    public static RecruitResponse from(Hero hero) {
        return new RecruitResponse(
                hero.getId(),
                hero.getName(),
                hero.getAlias(),
                hero.getHeroClass().name(),
                hero.getLevel(),
                hero.getHeroClass().getBaseHealth(),
                hero.getHeroClass().getBaseMana(),
                hero.getHeroClass().getHealthRecoveryPerSecond(),
                hero.getHeroClass().getManaRecoveryPerSecond());
    }
}
