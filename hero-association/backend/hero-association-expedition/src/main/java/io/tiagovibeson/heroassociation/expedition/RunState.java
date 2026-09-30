package io.tiagovibeson.heroassociation.expedition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroProgression;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattleSnapshot;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;

/** Only the current, unbanked expedition state is authoritative here. */
public record RunState(
        int schemaVersion,
        long stateVersion,
        UUID expeditionId,
        UUID ownerManagerId,
        UUID agencyId,
        UUID partyId,
        UUID mapId,
        int mapVersion,
        int encounterIndex,
        Phase phase,
        boolean returnRequested,
        Instant startedAt,
        List<HeroState> heroes,
        CreatureProfile creature,
        CarriedAssets carried,
        FightState fight,
        Outcome lastOutcome) {

    public static final int SCHEMA_VERSION = 1;

    public RunState {
        if (schemaVersion != SCHEMA_VERSION || stateVersion < 1 || mapVersion < 1 || encounterIndex < 1) {
            throw new IllegalArgumentException("Unsupported or invalid expedition state version.");
        }
        Objects.requireNonNull(expeditionId);
        Objects.requireNonNull(ownerManagerId);
        Objects.requireNonNull(agencyId);
        Objects.requireNonNull(partyId);
        Objects.requireNonNull(mapId);
        Objects.requireNonNull(phase);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(creature);
        Objects.requireNonNull(carried);
        heroes = List.copyOf(heroes);
        if (heroes.isEmpty() || heroes.stream().map(HeroState::heroId).distinct().count() != heroes.size()) {
            throw new IllegalArgumentException("An expedition needs distinct heroes.");
        }
        if ((phase == Phase.FIGHTING) != (fight != null)) {
            throw new IllegalArgumentException("Only a fighting run may contain an opening fight snapshot.");
        }
    }

    public RunState requestReturn() {
        if (phase != Phase.FIGHTING) {
            throw new IllegalStateException("Only a fighting run can defer return.");
        }
        return new RunState(schemaVersion, stateVersion + 1, expeditionId, ownerManagerId, agencyId,
                partyId, mapId, mapVersion, encounterIndex, phase, true, startedAt, heroes,
                creature, carried, fight, lastOutcome);
    }

    public RunState beginNext(FightState nextFight) {
        return new RunState(schemaVersion, stateVersion + 1, expeditionId, ownerManagerId, agencyId,
                partyId, mapId, mapVersion, encounterIndex + 1, Phase.FIGHTING, false, startedAt,
                heroes, creature, carried, nextFight, null);
    }

    public RunState finish(List<HeroState> updatedHeroes, Outcome outcome) {
        Phase next = returnRequested ? Phase.SETTLEMENT_PENDING
                : outcome.status() == CombatStatus.HERO_VICTORY ? Phase.AWAITING_CONTINUE : Phase.WIPED;
        return new RunState(schemaVersion, stateVersion + 1, expeditionId, ownerManagerId, agencyId,
                partyId, mapId, mapVersion, encounterIndex, next, false, startedAt,
                updatedHeroes, creature, carried, null, outcome);
    }

    public RunState returnBetweenFights() {
        if (phase != Phase.AWAITING_CONTINUE && phase != Phase.WIPED) {
            throw new IllegalStateException("Return must wait for the current fight.");
        }
        return new RunState(schemaVersion, stateVersion + 1, expeditionId, ownerManagerId, agencyId,
                partyId, mapId, mapVersion, encounterIndex, Phase.SETTLEMENT_PENDING, false,
                startedAt, heroes, creature, carried, null, lastOutcome);
    }

    public enum Phase {
        FIGHTING,
        AWAITING_CONTINUE,
        WIPED,
        SETTLEMENT_PENDING
    }

    public record HeroState(
            UUID heroId,
            String name,
            HeroClass heroClass,
            long experience,
            Map<HeroSkill, BigDecimal> skillPoints,
            int health,
            int mana,
            long staminaMilliseconds,
            Map<Integer, UUID> runeIds,
            double criticalChance,
            double criticalDamageMultiplier) {

        public HeroState {
            Objects.requireNonNull(heroId);
            Objects.requireNonNull(name);
            Objects.requireNonNull(heroClass);
            skillPoints = Map.copyOf(skillPoints);
            runeIds = Map.copyOf(runeIds);
            if (experience < 0 || staminaMilliseconds < 0
                    || staminaMilliseconds > HeroProgression.MAX_STAMINA_MILLISECONDS
                    || health < 0 || mana < 0 || criticalChance < 0 || criticalChance > 1
                    || criticalDamageMultiplier < 1) {
                throw new IllegalArgumentException("Invalid Hero resources or combat values.");
            }
            for (HeroSkill skill : HeroSkill.values()) {
                BigDecimal points = skillPoints.get(skill);
                if (points == null || points.signum() < 0 || points.scale() > 6) {
                    throw new IllegalArgumentException("All Hero skill totals need nonnegative six-decimal values.");
                }
            }
            if (health > maxHealth(heroClass, experience) || mana > maxMana(heroClass, experience)) {
                throw new IllegalArgumentException("Hero resources exceed their current maximum.");
            }
        }

        public int level() {
            return HeroProgression.levelForExperience(experience);
        }

        public int skillLevel(HeroSkill skill) {
            return HeroProgression.skillLevelForPoints(skillPoints.get(skill));
        }

        public int maxHealth() {
            return maxHealth(heroClass, experience);
        }

        public int maxMana() {
            return maxMana(heroClass, experience);
        }

        private static int maxHealth(HeroClass heroClass, long experience) {
            return Math.toIntExact((long) heroClass.getBaseHealth()
                    + (long) (HeroProgression.levelForExperience(experience) - 1) * heroClass.getHealthGainPerLevel());
        }

        private static int maxMana(HeroClass heroClass, long experience) {
            return Math.toIntExact((long) heroClass.getBaseMana()
                    + (long) (HeroProgression.levelForExperience(experience) - 1) * heroClass.getManaGainPerLevel());
        }
    }

    public record CreatureProfile(
            UUID definitionId,
            int definitionVersion,
            String name,
            int maxHealth,
            int maxMana,
            int attackDamage,
            long attackIntervalMilliseconds,
            int healthRecoveryPerSecond,
            int manaRecoveryPerSecond,
            double criticalChance,
            double criticalDamageMultiplier,
            int baseExperience) {
        public CreatureProfile {
            Objects.requireNonNull(definitionId);
            Objects.requireNonNull(name);
            if (definitionVersion < 1 || maxHealth < 1 || maxMana < 0 || attackDamage < 0
                    || attackIntervalMilliseconds < 1 || healthRecoveryPerSecond < 0
                    || manaRecoveryPerSecond < 0 || criticalChance < 0 || criticalChance > 1
                    || criticalDamageMultiplier < 1 || baseExperience < 0) {
                throw new IllegalArgumentException("Invalid pinned Creature definition.");
            }
        }
    }

    public record CarriedAssets(long gold, Map<UUID, Integer> items, Map<UUID, Integer> runes) {
        public CarriedAssets {
            items = Map.copyOf(items);
            runes = Map.copyOf(runes);
            if (gold < 0 || items.values().stream().anyMatch(quantity -> quantity < 0)
                    || runes.values().stream().anyMatch(quantity -> quantity < 0)) {
                throw new IllegalArgumentException("Carried assets cannot be negative.");
            }
        }

        public static CarriedAssets empty() {
            return new CarriedAssets(0, Map.of(), Map.of());
        }
    }

    public record FightState(UUID fightId, Instant startedAt, long randomSeed,
                             String rulesetVersion, String rngVersion, BigDecimal xpRate,
                             BigDecimal skillRate, BigDecimal dropRate,
                             CombatBattleSnapshot openingSnapshot) {
        public FightState {
            Objects.requireNonNull(fightId);
            Objects.requireNonNull(startedAt);
            Objects.requireNonNull(rulesetVersion);
            Objects.requireNonNull(rngVersion);
            Objects.requireNonNull(openingSnapshot);
            if (xpRate == null || skillRate == null || dropRate == null
                    || xpRate.signum() <= 0 || skillRate.signum() <= 0 || dropRate.signum() <= 0) {
                throw new IllegalArgumentException("Pinned event rates must be positive.");
            }
        }
    }

    public record Outcome(UUID fightId, CombatStatus status, long durationMilliseconds) {
        public Outcome {
            Objects.requireNonNull(fightId);
            Objects.requireNonNull(status);
            if (status == CombatStatus.IN_PROGRESS || durationMilliseconds < 0) {
                throw new IllegalArgumentException("A fight outcome must be terminal.");
            }
        }
    }
}
