package io.tiagovibeson.heroassociation.expedition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroSkill;

/** One immutable, aggregate owner update for an entire Expedition. */
public record SettlementEnvelope(int schemaVersion, UUID expeditionId, UUID ownerManagerId,
                                 UUID agencyId, UUID partyId, List<HeroFinal> heroes,
                                 long gold, Map<UUID, Integer> items, Map<UUID, Integer> runes) {

    public static final int SCHEMA_VERSION = 1;

    public SettlementEnvelope {
        if (schemaVersion != SCHEMA_VERSION || expeditionId == null || expeditionId.version() != 7
                || ownerManagerId == null || ownerManagerId.version() != 7
                || agencyId == null || agencyId.version() != 7
                || partyId == null || partyId.version() != 7 || gold < 0) {
            throw new IllegalArgumentException("Invalid Expedition settlement identity or gold.");
        }
        heroes = List.copyOf(heroes);
        items = Map.copyOf(items);
        runes = Map.copyOf(runes);
        if (heroes.isEmpty() || heroes.stream().map(HeroFinal::heroId).distinct().count() != heroes.size()
                || items.values().stream().anyMatch(quantity -> quantity == null || quantity < 0)
                || runes.values().stream().anyMatch(quantity -> quantity == null || quantity < 0)) {
            throw new IllegalArgumentException("Invalid Expedition settlement contents.");
        }
    }

    public static SettlementEnvelope from(RunState run) {
        if (run.phase() != RunState.Phase.SETTLEMENT_PENDING) {
            throw new IllegalArgumentException("Only a frozen Expedition can be settled.");
        }
        return new SettlementEnvelope(SCHEMA_VERSION, run.expeditionId(), run.ownerManagerId(),
                run.agencyId(), run.partyId(), run.heroes().stream().map(HeroFinal::from).toList(),
                run.carried().gold(), run.carried().items(), run.carried().runes());
    }

    public record HeroFinal(UUID heroId, HeroClass heroClass, long experience,
                            Map<HeroSkill, BigDecimal> skillPoints, int health, int mana,
                            long staminaMilliseconds) {
        public HeroFinal {
            Objects.requireNonNull(heroId);
            Objects.requireNonNull(heroClass);
            skillPoints = Map.copyOf(skillPoints);
            if (heroId.version() != 7 || experience < 0 || health < 0 || mana < 0
                    || staminaMilliseconds < 0) {
                throw new IllegalArgumentException("Invalid final Hero state.");
            }
            for (HeroSkill skill : HeroSkill.values()) {
                BigDecimal points = skillPoints.get(skill);
                if (points == null || points.signum() < 0 || points.scale() > 6) {
                    throw new IllegalArgumentException("Invalid final Hero skill points.");
                }
            }
        }

        private static HeroFinal from(RunState.HeroState hero) {
            return new HeroFinal(hero.heroId(), hero.heroClass(), hero.experience(),
                    hero.skillPoints(), hero.health(), hero.mana(), hero.staminaMilliseconds());
        }
    }
}
