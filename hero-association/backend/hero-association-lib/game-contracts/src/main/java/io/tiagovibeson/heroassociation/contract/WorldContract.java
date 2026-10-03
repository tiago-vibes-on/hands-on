package io.tiagovibeson.heroassociation.contract;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable wire inputs. A run pins a complete plan and never reads changing catalogs mid-fight. */
public final class WorldContract {
    private WorldContract() { }

    public static UUID id(UUID value) {
        if (value == null || value.version() != 7 || value.variant() != 2)
            throw new IllegalArgumentException("Resource IDs must be RFC 9562 UUIDv7.");
        return value;
    }

    public record GoldDrop(int minimumQuantity, int maximumQuantity, double chance) {
        public GoldDrop { dropRange(minimumQuantity, maximumQuantity, chance); }
    }

    public record Drop(String resourceType, UUID resourceId, int minimumQuantity, int maximumQuantity, double chance) {
        public Drop {
            id(resourceId);
            dropRange(minimumQuantity, maximumQuantity, chance);
            if (!("ITEM".equals(resourceType) || "RUNE".equals(resourceType)))
                throw new IllegalArgumentException("Invalid Creature drop.");
        }
    }

    private static void dropRange(int minimumQuantity, int maximumQuantity, double chance) {
        if (minimumQuantity < 1 || maximumQuantity < minimumQuantity
                || !Double.isFinite(chance) || chance < 0 || chance > 1)
            throw new IllegalArgumentException("Invalid Creature drop range or chance.");
    }

    public record Creature(UUID definitionId, int version, String name, int baseExperience,
                           int maxHealth, int maxMana, int attackDamage, long attackIntervalMilliseconds,
                           int healthRecoveryPerSecond, int manaRecoveryPerSecond, double criticalChance,
                           double criticalDamageMultiplier, GoldDrop goldDrop, List<Drop> drops) {
        public Creature {
            id(definitionId);
            Objects.requireNonNull(goldDrop);
            drops = List.copyOf(drops);
            if (version < 1 || name == null || name.isBlank() || name.length() > 100
                    || baseExperience < 0 || maxHealth < 1 || maxMana < 0 || attackDamage < 0
                    || attackIntervalMilliseconds < 1 || healthRecoveryPerSecond < 0 || manaRecoveryPerSecond < 0
                    || !Double.isFinite(criticalChance) || criticalChance < 0 || criticalChance > 1
                    || !Double.isFinite(criticalDamageMultiplier) || criticalDamageMultiplier < 1
                    || drops.size() > 32)
                throw new IllegalArgumentException("Invalid Creature definition.");
        }
    }

    public enum MapKind { FIELD, DUNGEON }

    public record Spawn(UUID creatureId, int creatureVersion, int count) {
        public Spawn {
            id(creatureId);
            if (creatureVersion < 1 || count < 1 || count > 8)
                throw new IllegalArgumentException("Invalid encounter spawn.");
        }
    }

    public record Encounter(UUID encounterId, String name, boolean boss, List<Spawn> spawns) {
        public Encounter {
            id(encounterId);
            spawns = List.copyOf(spawns);
            if (name == null || name.isBlank() || spawns.isEmpty() || spawns.size() > 8
                    || spawns.stream().mapToInt(Spawn::count).sum() > 8)
                throw new IllegalArgumentException("An encounter needs one to eight Creatures.");
        }
    }

    public record Floor(int number, String name, String layout, List<Encounter> encounters) {
        public Floor {
            encounters = List.copyOf(encounters);
            if (number < 1 || name == null || name.isBlank() || layout == null || layout.isBlank()
                    || encounters.isEmpty() || encounters.size() > 32)
                throw new IllegalArgumentException("Invalid Map floor.");
        }
    }

    public record MapDefinition(UUID definitionId, int version, String name, MapKind kind, List<Floor> floors) {
        public MapDefinition {
            id(definitionId);
            Objects.requireNonNull(kind);
            floors = List.copyOf(floors);
            if (version < 1 || name == null || name.isBlank() || floors.isEmpty() || floors.size() > 32
                    || kind == MapKind.FIELD && floors.size() != 1)
                throw new IllegalArgumentException("Invalid Map definition.");
            for (int index = 0; index < floors.size(); index++) {
                if (floors.get(index).number() != index + 1)
                    throw new IllegalArgumentException("Map floors must be consecutive.");
            }
            var encounters = floors.stream().flatMap(floor -> floor.encounters().stream()).toList();
            if (encounters.stream().map(Encounter::encounterId).distinct().count() != encounters.size())
                throw new IllegalArgumentException("Encounter IDs must be distinct within a Map version.");
        }

        public int encounterCount() { return floors.stream().mapToInt(floor -> floor.encounters().size()).sum(); }
        public boolean completedAfter(int encounterIndex) { return kind == MapKind.DUNGEON && encounterIndex >= encounterCount(); }
        public Encounter encounter(int encounterIndex) {
            if (encounterIndex < 1 || kind == MapKind.DUNGEON && encounterIndex > encounterCount())
                throw new IllegalArgumentException("No encounter exists at this Map position.");
            int offset = (encounterIndex - 1) % encounterCount();
            for (Floor floor : floors) {
                if (offset < floor.encounters().size()) return floor.encounters().get(offset);
                offset -= floor.encounters().size();
            }
            throw new IllegalStateException("Map position could not be resolved.");
        }
        public int floorNumber(int encounterIndex) {
            encounter(encounterIndex);
            int offset = (encounterIndex - 1) % encounterCount();
            for (Floor floor : floors) {
                if (offset < floor.encounters().size()) return floor.number();
                offset -= floor.encounters().size();
            }
            throw new IllegalArgumentException("Invalid Map position.");
        }
    }

    public record Plan(MapDefinition map, List<Creature> creatures) {
        public Plan {
            Objects.requireNonNull(map);
            creatures = List.copyOf(creatures);
            if (creatures.isEmpty() || creatures.size() > 128
                    || creatures.stream().map(creature -> creature.definitionId() + ":" + creature.version()).distinct().count() != creatures.size())
                throw new IllegalArgumentException("A pinned Map needs distinct Creature versions.");
            for (Floor floor : map.floors()) for (Encounter encounter : floor.encounters()) for (Spawn spawn : encounter.spawns()) {
                if (creatures.stream().noneMatch(creature -> creature.definitionId().equals(spawn.creatureId()) && creature.version() == spawn.creatureVersion()))
                    throw new IllegalArgumentException("Map refers to an unavailable Creature version.");
            }
        }
        public Creature creature(Spawn spawn) {
            return creatures.stream().filter(value -> value.definitionId().equals(spawn.creatureId()) && value.version() == spawn.creatureVersion())
                    .findFirst().orElseThrow();
        }
    }
}
