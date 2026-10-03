package io.tiagovibeson.heroassociation.contract;

import java.util.*;
import static io.tiagovibeson.heroassociation.contract.WorldContract.id;

public final class QuestContract {
    private QuestContract() { }
    public enum Objective { KILL_COUNT, BOSS_DEFEAT, DUNGEON_COMPLETION }

    public record Reward(long gold, Map<UUID, Integer> items, Map<UUID, Integer> runes) {
        public Reward {
            items = inventory(items); runes = inventory(runes);
            if (gold < 0) throw new IllegalArgumentException("Quest rewards cannot be negative.");
        }
        private static Map<UUID, Integer> inventory(Map<UUID, Integer> values) {
            values = Map.copyOf(values);
            if (values.size() > 128) throw new IllegalArgumentException("Too many Quest reward resources.");
            values.forEach((resource, quantity) -> { id(resource); if (quantity < 1) throw new IllegalArgumentException("Reward quantities must be positive."); });
            return values;
        }
    }

    public record Definition(UUID definitionId, int version, String title, String description, Objective objective,
                             UUID creatureId, int required, Set<UUID> mapIds, Reward reward) {
        public Definition {
            id(definitionId); Objects.requireNonNull(objective); Objects.requireNonNull(reward);
            mapIds = Set.copyOf(mapIds); mapIds.forEach(WorldContract::id);
            if (creatureId != null) id(creatureId);
            if (version < 1 || title == null || title.isBlank() || description == null || description.isBlank()
                    || required < 1 || required > 1_000_000 || mapIds.size() > 32
                    || objective == Objective.KILL_COUNT && creatureId == null
                    || objective == Objective.DUNGEON_COMPLETION && (mapIds.isEmpty() || creatureId != null))
                throw new IllegalArgumentException("Invalid Quest definition.");
        }
        public boolean eligible(UUID mapId) { return mapIds.isEmpty() || mapIds.contains(mapId); }
    }

    public record Assignment(UUID assignmentId, UUID ownerManagerId, Definition definition, int progress, String status) {
        public Assignment {
            id(assignmentId); id(ownerManagerId); Objects.requireNonNull(definition);
            if (progress < 0 || progress > definition.required()
                    || !Set.of("ACTIVE", "REWARD_PENDING", "COMPLETED", "CANCELLED").contains(status))
                throw new IllegalArgumentException("Invalid Quest assignment.");
        }
    }

    public record Pin(UUID expeditionId, UUID ownerManagerId, UUID agencyId, UUID mapId, int mapVersion,
                      Assignment assignment, boolean eligible) {
        public Pin {
            id(expeditionId); id(ownerManagerId); id(agencyId); id(mapId);
            if (mapVersion < 1 || assignment != null && (!ownerManagerId.equals(assignment.ownerManagerId())
                    || !"ACTIVE".equals(assignment.status()) || eligible != assignment.definition().eligible(mapId))
                    || assignment == null && eligible)
                throw new IllegalArgumentException("Invalid Expedition Quest pin.");
        }
    }

    public record Progress(Pin pin, int progress) {
        public Progress {
            Objects.requireNonNull(pin);
            if (pin.assignment() == null || progress < pin.assignment().progress() || progress > pin.assignment().definition().required()
                    || !pin.eligible() && progress != pin.assignment().progress())
                throw new IllegalArgumentException("Invalid Expedition Quest progress.");
        }
        public boolean completed() { return progress >= pin.assignment().definition().required(); }
        public Progress advance(Map<UUID, Integer> kills, boolean bossDefeated, boolean dungeonCompleted) {
            if (!pin.eligible() || completed()) return this;
            Definition quest = pin.assignment().definition();
            int increase = switch (quest.objective()) {
                case KILL_COUNT -> kills.getOrDefault(quest.creatureId(), 0);
                case BOSS_DEFEAT -> bossDefeated && (quest.creatureId() == null || kills.getOrDefault(quest.creatureId(), 0) > 0) ? 1 : 0;
                case DUNGEON_COMPLETION -> dungeonCompleted ? 1 : 0;
            };
            return new Progress(pin, Math.min(quest.required(), Math.addExact(progress, increase)));
        }
    }

    public record PinRequest(UUID expeditionId, UUID ownerManagerId, UUID agencyId, UUID mapId, int mapVersion) {
        public PinRequest { id(expeditionId); id(ownerManagerId); id(agencyId); id(mapId); if (mapVersion < 1) throw new IllegalArgumentException("Map version must be positive."); }
    }
    public record ReturnRequest(UUID expeditionId, UUID ownerManagerId, UUID agencyId, UUID mapId, int mapVersion, Progress progress) {
        public ReturnRequest {
            id(expeditionId); id(ownerManagerId); id(agencyId); id(mapId);
            if (mapVersion < 1 || progress != null && (!expeditionId.equals(progress.pin().expeditionId())
                    || !ownerManagerId.equals(progress.pin().ownerManagerId()) || !agencyId.equals(progress.pin().agencyId())
                    || !mapId.equals(progress.pin().mapId()) || mapVersion != progress.pin().mapVersion()))
                throw new IllegalArgumentException("Quest return differs from the pinned Expedition.");
        }
    }
    public record ReturnReceipt(UUID expeditionId, UUID ownerManagerId, String status, UUID assignmentId) { }
}
