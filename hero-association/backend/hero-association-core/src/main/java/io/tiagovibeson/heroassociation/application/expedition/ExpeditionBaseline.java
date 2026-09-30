package io.tiagovibeson.heroassociation.application.expedition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroSkill;

/** The immutable Core-side comparison point for one reserved Party. */
public record ExpeditionBaseline(List<Hero> heroes) {
    public ExpeditionBaseline {
        heroes = List.copyOf(heroes);
    }

    public record Hero(UUID heroId, String name, HeroClass heroClass, long experience,
                       Map<HeroSkill, BigDecimal> skillPoints, int health, int mana,
                       long staminaMilliseconds, HeroActivity previousActivity,
                       Map<Integer, UUID> runeIds, double criticalChance,
                       double criticalDamageMultiplier) {
        public Hero {
            skillPoints = Map.copyOf(skillPoints);
            runeIds = Map.copyOf(runeIds);
        }
    }
}
