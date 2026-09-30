package io.tiagovibeson.heroassociation.combat.application;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tiagovibeson.heroassociation.combat.api.StartBattleRequest;
import io.tiagovibeson.heroassociation.combat.api.StartBattleResponse;
import io.tiagovibeson.heroassociation.combat.domain.BattleRecord;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattle;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattleSnapshot;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;
import io.tiagovibeson.heroassociation.domain.combat.CombatSpell;
import io.tiagovibeson.heroassociation.domain.combat.CombatantSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;

@ApplicationScoped
public class BattleStartService {

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public BattleStartService(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StartBattleResponse start(StartBattleRequest request) {
        CombatBattleSnapshot initialState = validate(request);
        JsonNode requestedPayload = objectMapper.valueToTree(request);
        String requestJson = requestedPayload.toString();
        String snapshotJson = objectMapper.valueToTree(initialState).toString();
        Instant now = Instant.now();

        int inserted = entityManager.createNativeQuery("""
                INSERT INTO battle (id, request_json, snapshot_json, created_at, last_advanced_at, status, next_event_sequence, next_fact_sequence)
                VALUES (:id, :requestJson, :snapshotJson, :createdAt, :createdAt, 'PREPARED', 1, 1)
                ON CONFLICT (id) DO NOTHING
                """)
                .setParameter("id", request.battleId())
                .setParameter("requestJson", requestJson)
                .setParameter("snapshotJson", snapshotJson)
                .setParameter("createdAt", now)
                .executeUpdate();

        BattleRecord record = entityManager.find(BattleRecord.class, request.battleId());
        if (record == null) {
            throw new IllegalStateException("Battle start was not persisted.");
        }
        if (inserted == 0 && !samePayload(record.getRequestJson(), request)) {
            throw new WebApplicationException("Battle ID already belongs to a different start request.", 409);
        }
        return new StartBattleResponse(record.getId(), record.getStatus(), record.getCreatedAt(), inserted == 0);
    }

    private boolean samePayload(String storedJson, StartBattleRequest request) {
        try {
            return objectMapper.readValue(storedJson, StartBattleRequest.class).equals(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored battle start request is invalid.", exception);
        }
    }

    private CombatBattleSnapshot validate(StartBattleRequest request) {
        if (request == null
                || !isUuidV7(request.battleId())
                || request.source() == null
                || !isUuidV7(request.runId())
                || !isUuidV7(request.partyId())
                || !isUuidV7(request.ownerManagerId())
                || !"core-v1".equals(request.rulesetVersion())
                || request.initialSnapshot() == null) {
            throw new BadRequestException("Battle start requires UUIDv7 IDs and the core-v1 ruleset.");
        }
        CombatBattleSnapshot snapshot = validateSnapshot(request.initialSnapshot());
        BattleInputValidator.validate(request, snapshot);
        return snapshot;
    }

    private CombatBattleSnapshot validateSnapshot(CombatBattleSnapshot snapshot) {
        if (snapshot.status() != CombatStatus.IN_PROGRESS
                || snapshot.currentTimeMilliseconds() != 0
                || snapshot.nextRecoveryAt() != 1_000
                || snapshot.heroes().isEmpty()
                || snapshot.creatures().isEmpty()) {
            throw new BadRequestException("A new battle needs a nonempty, in-progress opening snapshot.");
        }

        Set<UUID> combatantIds = new HashSet<>();
        snapshot.heroes().forEach(combatant -> validateCombatant(combatant, combatantIds));
        snapshot.creatures().forEach(combatant -> validateCombatant(combatant, combatantIds));
        boolean livingHero = snapshot.heroes().stream().anyMatch(combatant -> combatant.currentHealth() > 0);
        boolean livingCreature = snapshot.creatures().stream().anyMatch(combatant -> combatant.currentHealth() > 0);
        if (!livingHero || !livingCreature) {
            throw new BadRequestException("A new battle needs living Heroes and Creatures.");
        }

        try {
            return CombatBattle.restore(snapshot).snapshot();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BadRequestException("Invalid combatant stats or team.", exception);
        }
    }

    private void validateCombatant(CombatantSnapshot combatant, Set<UUID> seen) {
        try {
            UUID id = UUID.fromString(combatant.id());
            Set<CombatSpell> eligibleSpells = new HashSet<>(combatant.spells());
            eligibleSpells.removeIf(spell -> combatant.magicLevel() < spell.getRequiredMagicLevel());
            if (!isUuidV7(id)
                    || !seen.add(id)
                    || combatant.nextBasicAttackAt() <= 0
                    || combatant.spells().size() != Set.copyOf(combatant.spells()).size()
                    || !combatant.nextSpellCastAt().keySet().equals(eligibleSpells)
                    || combatant.nextSpellCastAt().values().stream().anyMatch(time -> time <= 0)
                    || !Double.isFinite(combatant.criticalChance())
                    || !Double.isFinite(combatant.criticalDamageMultiplier())) {
                throw new BadRequestException("Combatants need unique UUIDv7 IDs and valid starting timers.");
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BadRequestException("Invalid combatant ID or timer.", exception);
        }
    }

    private boolean isUuidV7(UUID value) {
        return value != null && value.version() == 7;
    }
}
