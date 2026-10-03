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
        Outcome lastOutcome,
        io.tiagovibeson.heroassociation.contract.WorldContract.Plan world,
        io.tiagovibeson.heroassociation.contract.QuestContract.Progress quest) {

    public static final int SCHEMA_VERSION = 2;

    public RunState(int schemaVersion, long stateVersion, UUID expeditionId, UUID ownerManagerId, UUID agencyId,
                    UUID partyId, UUID mapId, int mapVersion, int encounterIndex, Phase phase, boolean returnRequested,
                    Instant startedAt, List<HeroState> heroes, CreatureProfile creature, CarriedAssets carried,
                    FightState fight, Outcome lastOutcome, io.tiagovibeson.heroassociation.contract.WorldContract.Plan world) {
        this(schemaVersion, stateVersion, expeditionId, ownerManagerId, agencyId, partyId, mapId, mapVersion,
                encounterIndex, phase, returnRequested, startedAt, heroes, creature, carried, fight, lastOutcome, world, null);
    }

    public RunState(int schemaVersion, long stateVersion, UUID expeditionId, UUID ownerManagerId, UUID agencyId,
                    UUID partyId, UUID mapId, int mapVersion, int encounterIndex, Phase phase, boolean returnRequested,
                    Instant startedAt, List<HeroState> heroes, CreatureProfile creature, CarriedAssets carried,
                    FightState fight, Outcome lastOutcome) {
        this(schemaVersion, stateVersion, expeditionId, ownerManagerId, agencyId, partyId, mapId, mapVersion,
                encounterIndex, phase, returnRequested, startedAt, heroes, creature, carried, fight, lastOutcome,
                WorldPlans.field(mapId, mapVersion, creature));
    }

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
        Objects.requireNonNull(world);
        if (!mapId.equals(world.map().definitionId()) || mapVersion != world.map().version())
            throw new IllegalArgumentException("Run Map identity differs from its pinned plan.");
        world.map().encounter(encounterIndex);
        if (quest != null && (!expeditionId.equals(quest.pin().expeditionId()) || !ownerManagerId.equals(quest.pin().ownerManagerId())
                || !agencyId.equals(quest.pin().agencyId()) || !mapId.equals(quest.pin().mapId()) || mapVersion != quest.pin().mapVersion()))
            throw new IllegalArgumentException("Run Quest identity differs from admission.");
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
                creature, carried, fight, lastOutcome, world, quest);
    }

    public boolean canContinue() {
        return (phase == Phase.AWAITING_CONTINUE || phase == Phase.DUNGEON_COMPLETED)
                && !returnRequested && heroes.stream().anyMatch(hero -> hero.health() > 0);
    }

    public int nextEncounterIndex() {
        if (!canContinue()) throw new IllegalStateException("Expedition cannot continue from this phase.");
        return phase == Phase.DUNGEON_COMPLETED ? 1 : Math.addExact(encounterIndex, 1);
    }

    public RunState beginNext(FightState nextFight) {
        int nextIndex = nextEncounterIndex();
        return new RunState(schemaVersion, stateVersion + 1, expeditionId, ownerManagerId, agencyId,
                partyId, mapId, mapVersion, nextIndex, Phase.FIGHTING, false, startedAt,
                heroes, WorldPlans.profile(world.creature(world.map().encounter(nextIndex).spawns().getFirst())), carried, nextFight, null, world, quest);
    }

    public RunState finish(List<HeroState> updatedHeroes, Outcome outcome) {
        Phase next = returnRequested ? Phase.SETTLEMENT_PENDING
                : outcome.status() == CombatStatus.HERO_VICTORY
                    ? world.map().completedAfter(encounterIndex) ? Phase.DUNGEON_COMPLETED : Phase.AWAITING_CONTINUE
                    : Phase.WIPED;
        return new RunState(schemaVersion, stateVersion + 1, expeditionId, ownerManagerId, agencyId,
                partyId, mapId, mapVersion, encounterIndex, next, false, startedAt,
                updatedHeroes, creature, carried.plus(outcome.loot()), null, outcome, world,
                quest == null ? null : quest.advance(outcome.kills(),
                        outcome.status() == CombatStatus.HERO_VICTORY && world.map().encounter(encounterIndex).boss(),
                        outcome.status() == CombatStatus.HERO_VICTORY && world.map().completedAfter(encounterIndex)));
    }

    public RunState returnBetweenFights() {
        if (phase != Phase.AWAITING_CONTINUE && phase != Phase.WIPED && phase != Phase.DUNGEON_COMPLETED) {
            throw new IllegalStateException("Return must wait for the current fight.");
        }
        return new RunState(schemaVersion, stateVersion + 1, expeditionId, ownerManagerId, agencyId,
                partyId, mapId, mapVersion, encounterIndex, Phase.SETTLEMENT_PENDING, false,
                startedAt, heroes, creature, carried, null, lastOutcome, world, quest);
    }

    public enum Phase {
        FIGHTING,
        AWAITING_CONTINUE,
        WIPED,
        DUNGEON_COMPLETED,
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

        public CarriedAssets plus(CarriedAssets reward) {
            return new CarriedAssets(Math.addExact(gold, reward.gold()), merge(items, reward.items()), merge(runes, reward.runes()));
        }
        private static Map<UUID, Integer> merge(Map<UUID, Integer> first, Map<UUID, Integer> second) {
            Map<UUID, Integer> result = new java.util.TreeMap<>(first);
            second.forEach((id, amount) -> result.merge(id, amount, Math::addExact));
            if (result.size() > 128) throw new IllegalArgumentException("Carried inventory has too many resource kinds.");
            return result;
        }
    }

    public record FightState(UUID fightId, Instant startedAt, long randomSeed,
                             String rulesetVersion, String rngVersion, BigDecimal xpRate,
                             BigDecimal skillRate, BigDecimal dropRate,
                             CombatBattleSnapshot openingSnapshot,
                             Map<String, io.tiagovibeson.heroassociation.contract.WorldContract.Creature> creatures) {
        public FightState(UUID fightId, Instant startedAt, long randomSeed, String rulesetVersion, String rngVersion,
                          BigDecimal xpRate, BigDecimal skillRate, BigDecimal dropRate, CombatBattleSnapshot openingSnapshot) {
            this(fightId, startedAt, randomSeed, rulesetVersion, rngVersion, xpRate, skillRate, dropRate, openingSnapshot, Map.of());
        }
        public FightState {
            Objects.requireNonNull(fightId);
            Objects.requireNonNull(startedAt);
            Objects.requireNonNull(rulesetVersion);
            Objects.requireNonNull(rngVersion);
            Objects.requireNonNull(openingSnapshot);
            creatures = Map.copyOf(creatures);
            if (!creatures.isEmpty() && !creatures.keySet().equals(openingSnapshot.creatures().stream()
                    .map(io.tiagovibeson.heroassociation.domain.combat.CombatantSnapshot::id).collect(java.util.stream.Collectors.toSet())))
                throw new IllegalArgumentException("Fight Creature versions do not match its combatants.");
            if (xpRate == null || skillRate == null || dropRate == null
                    || xpRate.signum() <= 0 || skillRate.signum() <= 0 || dropRate.signum() <= 0) {
                throw new IllegalArgumentException("Pinned event rates must be positive.");
            }
        }
    }

    public record Outcome(UUID fightId, CombatStatus status, long durationMilliseconds,
                          Map<UUID, Integer> kills, CarriedAssets loot) {
        public Outcome(UUID fightId, CombatStatus status, long durationMilliseconds) {
            this(fightId, status, durationMilliseconds, Map.of(), CarriedAssets.empty());
        }
        public Outcome {
            Objects.requireNonNull(fightId);
            Objects.requireNonNull(status);
            kills = Map.copyOf(kills);
            Objects.requireNonNull(loot);
            if (kills.size() > 8 || kills.values().stream().anyMatch(count -> count == null || count < 0 || count > 8))
                throw new IllegalArgumentException("Invalid encounter kill totals.");
            if (status == CombatStatus.IN_PROGRESS || durationMilliseconds < 0) {
                throw new IllegalArgumentException("A fight outcome must be terminal.");
            }
        }
    }
}
