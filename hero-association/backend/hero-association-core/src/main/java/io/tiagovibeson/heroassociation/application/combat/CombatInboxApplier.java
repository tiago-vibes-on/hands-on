package io.tiagovibeson.heroassociation.application.combat;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.tiagovibeson.heroassociation.domain.CombatBattleRegistration;
import io.tiagovibeson.heroassociation.domain.CombatProgressionInboxRecord;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroProgression;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.domain.combat.CombatAction;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CombatInboxApplier {

    private static final BigDecimal MAGIC_POINTS_PER_MANA = new BigDecimal("0.05");

    private final EntityManager entityManager;
    private final ObjectMapper mapper;

    public CombatInboxApplier(EntityManager entityManager, ObjectMapper mapper) {
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    /**
     * Called only after another owner has taken over this Party's battle. No live caller exists yet.
     */
    @Transactional
    public void register(UUID battleId, UUID partyId, Map<UUID, UUID> heroIdsByCombatant) {
        requireUuidV7(battleId);
        requireUuidV7(partyId);
        if (heroIdsByCombatant == null || heroIdsByCombatant.isEmpty()
                || heroIdsByCombatant.size() > 4
                || new HashSet<>(heroIdsByCombatant.values()).size() != heroIdsByCombatant.size()) {
            throw new IllegalArgumentException("Combat registration needs one to four distinct Heroes.");
        }
        ObjectNode bindings = mapper.createObjectNode();
        heroIdsByCombatant.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    requireUuidV7(entry.getKey());
                    requireUuidV7(entry.getValue());
                    bindings.put(entry.getKey().toString(), entry.getValue().toString());
                });
        String bindingsJson = bindings.toString();
        CombatBattleRegistration existing = entityManager.find(CombatBattleRegistration.class, battleId);
        if (existing != null) {
            if (!partyId.equals(existing.getPartyId())
                    || !bindingsJson.equals(existing.getHeroBindingsJson())) {
                throw new IllegalArgumentException("Battle ID was registered with different Hero bindings.");
            }
            return;
        }
        Party party = entityManager.find(Party.class, partyId);
        if (party == null || party.getQuest() != null) {
            throw new IllegalArgumentException("Combat registration requires a Party outside the live Core Quest writer.");
        }
        for (UUID heroId : heroIdsByCombatant.values()) {
            Hero hero = entityManager.find(Hero.class, heroId);
            if (hero == null || hero.getParty() == null
                    || !partyId.equals(hero.getParty().getId())
                    || hero.getActivity() != HeroActivity.ON_QUEST) {
                throw new IllegalArgumentException("A registered Hero must be active in the specified Party.");
            }
        }
        entityManager.persist(new CombatBattleRegistration(battleId, partyId, bindingsJson, Instant.now()));
        entityManager.flush();
    }

    /**
     * Applies one contiguous batch to Hero state and its cursor in the same transaction.
     * There is no scheduled caller before Combat takes ownership of live battles.
     */
    @Transactional
    public boolean applyNext(UUID battleId) {
        CombatBattleRegistration registration = entityManager.find(
                CombatBattleRegistration.class, battleId, LockModeType.PESSIMISTIC_WRITE);
        if (registration == null) {
            throw new IllegalArgumentException("Combat battle is not registered for Hero progression.");
        }
        Party party = entityManager.find(Party.class, registration.getPartyId());
        if (party == null || party.getQuest() != null) {
            throw new IllegalStateException("A live Core Quest cannot share this Combat progression Party.");
        }
        if (registration.getCompletedAt() != null) {
            return false;
        }
        CombatProgressionInboxRecord batch = entityManager.createQuery(
                        "select i from CombatProgressionInboxRecord i "
                                + "where i.battleId = :battleId and i.firstSequence = :sequence and i.appliedAt is null",
                        CombatProgressionInboxRecord.class)
                .setParameter("battleId", battleId)
                .setParameter("sequence", registration.getNextSequence())
                .getResultStream().findFirst().orElse(null);
        if (batch == null) {
            return false;
        }
        JsonNode envelope = parse(batch.getPayloadJson());
        if (!batch.getId().equals(uuid(envelope.path("batchId")))
                || !battleId.equals(uuid(envelope.path("battleId")))
                || batch.getFirstSequence() != nonnegativeLong(envelope.path("firstSequence"))
                || batch.getLastSequence() != nonnegativeLong(envelope.path("lastSequence"))) {
            throw new IllegalArgumentException("Stored Combat batch metadata does not match its envelope.");
        }
        JsonNode facts = envelope.path("facts");
        if (!facts.isArray() || facts.isEmpty()
                || facts.size() != batch.getLastSequence() - batch.getFirstSequence() + 1) {
            throw new IllegalArgumentException("Stored Combat batch has an invalid fact range.");
        }
        Map<UUID, UUID> bindings = bindings(registration.getHeroBindingsJson());
        Map<UUID, Hero> heroes = new HashMap<>();
        long lastTime = registration.getLastFactAtMilliseconds();
        boolean completed = false;
        for (int index = 0; index < facts.size(); index++) {
            JsonNode fact = facts.get(index);
            long time = nonnegativeLong(fact.path("occurredAtMilliseconds"));
            if (nonnegativeLong(fact.path("sequence")) != batch.getFirstSequence() + index
                    || time < lastTime || completed) {
                throw new IllegalArgumentException("Combat facts are not ordered after the applied cursor.");
            }
            lastTime = time;
            switch (text(fact.path("type"))) {
                case "STAMINA_ELAPSED" -> consumeStamina(
                        fact, bindings, heroes, registration.getPartyId());
                case "HERO_ACTION" -> awardAction(
                        fact, bindings, heroes, registration.getPartyId());
                case "CREATURE_KILLED" -> awardKill(
                        fact, bindings, heroes, registration.getPartyId());
                case "HERO_FELL" -> hero(
                        uuid(fact.path("heroId")), bindings, heroes, registration.getPartyId());
                case "BATTLE_COMPLETED" -> {
                    complete(fact, bindings, heroes, registration.getPartyId());
                    completed = true;
                }
                default -> throw new IllegalArgumentException("Unsupported Combat progression fact.");
            }
        }
        Instant appliedAt = Instant.now();
        registration.advance(batch.getLastSequence(), lastTime, completed, appliedAt);
        batch.markApplied(appliedAt);
        entityManager.flush();
        return true;
    }

    private void consumeStamina(
            JsonNode fact, Map<UUID, UUID> bindings, Map<UUID, Hero> heroes, UUID partyId) {
        long elapsed = nonnegativeLong(fact.path("elapsedMilliseconds"));
        if (elapsed == 0) {
            throw new IllegalArgumentException("Elapsed battle time must be positive.");
        }
        for (UUID heroId : recipients(fact.path("heroIds"), bindings, false)) {
            hero(heroId, bindings, heroes, partyId).consumeStaminaMilliseconds(elapsed);
        }
    }

    private void awardAction(
            JsonNode fact, Map<UUID, UUID> bindings, Map<UUID, Hero> heroes, UUID partyId) {
        Hero hero = hero(uuid(fact.path("heroId")), bindings, heroes, partyId);
        CombatAction action = CombatAction.valueOf(text(fact.path("action")));
        int manaSpent = nonnegativeInt(fact.path("manaSpent"));
        if (action == CombatAction.BASIC_ATTACK) {
            if (hero.getHeroClass() == HeroClass.WARRIOR) {
                awardSkill(hero, HeroSkill.MELEE, BigDecimal.ONE);
            } else if (hero.getHeroClass() == HeroClass.ARCHER) {
                awardSkill(hero, HeroSkill.DISTANCE, BigDecimal.ONE);
            }
        }
        if (manaSpent > 0) {
            awardSkill(hero, HeroSkill.MAGIC,
                    BigDecimal.valueOf(manaSpent).multiply(MAGIC_POINTS_PER_MANA));
        }
    }

    private void awardKill(
            JsonNode fact, Map<UUID, UUID> bindings, Map<UUID, Hero> heroes, UUID partyId) {
        uuid(fact.path("creatureCombatantId"));
        int baseExperience = nonnegativeInt(fact.path("baseExperience"));
        for (UUID heroId : recipients(fact.path("heroIds"), bindings, true)) {
            Hero hero = hero(heroId, bindings, heroes, partyId);
            hero.addExperience(HeroProgression.creatureExperienceAward(
                    baseExperience, hero.getStaminaMilliseconds()));
        }
    }

    private void complete(
            JsonNode fact, Map<UUID, UUID> bindings, Map<UUID, Hero> heroes, UUID partyId) {
        JsonNode snapshot = fact.path("finalSnapshot");
        CombatStatus status = CombatStatus.valueOf(text(snapshot.path("status")));
        JsonNode finalHeroes = snapshot.path("heroes");
        if (status == CombatStatus.IN_PROGRESS
                || !finalHeroes.isArray() || finalHeroes.size() != bindings.size()) {
            throw new IllegalArgumentException("Combat completion needs a terminal snapshot of every Hero.");
        }
        Set<UUID> seen = new HashSet<>();
        Instant synchronizedAt = Instant.now();
        for (JsonNode finalHero : finalHeroes) {
            UUID combatantId = uuid(finalHero.path("id"));
            UUID heroId = bindings.get(combatantId);
            if (heroId == null || !seen.add(combatantId)) {
                throw new IllegalArgumentException("Terminal Hero is not in the registered formation.");
            }
            Hero hero = hero(heroId, bindings, heroes, partyId);
            int health = nonnegativeInt(finalHero.path("currentHealth"));
            int mana = nonnegativeInt(finalHero.path("currentMana"));
            if (health > hero.getMaxHealth() || mana > hero.getMaxMana()) {
                throw new IllegalArgumentException("Terminal Hero resources exceed their current limits.");
            }
            hero.synchronizeCombatResources(health, mana, synchronizedAt);
        }
    }

    private void awardSkill(Hero hero, HeroSkill skill, BigDecimal basePoints) {
        hero.addSkillPoints(skill, HeroProgression.combatSkillAward(
                hero.getHeroClass(), skill, basePoints, hero.getStaminaMilliseconds()));
    }

    private Hero hero(
            UUID heroId, Map<UUID, UUID> bindings, Map<UUID, Hero> heroes, UUID partyId) {
        if (!bindings.containsValue(heroId)) {
            throw new IllegalArgumentException("Combat fact references an unregistered Hero.");
        }
        return heroes.computeIfAbsent(heroId, id -> {
            Hero found = entityManager.find(Hero.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (found == null || found.getParty() == null
                    || !partyId.equals(found.getParty().getId())
                    || found.getActivity() != HeroActivity.ON_QUEST) {
                throw new IllegalArgumentException("Registered Hero is no longer active in this Party.");
            }
            return found;
        });
    }

    private Set<UUID> recipients(JsonNode value, Map<UUID, UUID> bindings, boolean allowEmpty) {
        if (!value.isArray() || !allowEmpty && value.isEmpty()) {
            throw new IllegalArgumentException("Combat fact requires Hero recipients.");
        }
        Set<UUID> recipients = new HashSet<>();
        for (JsonNode item : value) {
            UUID heroId = uuid(item);
            if (!bindings.containsValue(heroId) || !recipients.add(heroId)) {
                throw new IllegalArgumentException("Combat fact has invalid Hero recipients.");
            }
        }
        return recipients;
    }

    private Map<UUID, UUID> bindings(String json) {
        JsonNode stored = parse(json);
        if (!stored.isObject()) {
            throw new IllegalArgumentException("Registered Hero bindings are invalid.");
        }
        Map<UUID, UUID> bindings = new HashMap<>();
        stored.fields().forEachRemaining(entry ->
                bindings.put(requireUuidV7(UUID.fromString(entry.getKey())), uuid(entry.getValue())));
        return bindings;
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Stored Combat JSON is invalid.", exception);
        }
    }

    private UUID uuid(JsonNode value) {
        return requireUuidV7(UUID.fromString(text(value)));
    }

    private UUID requireUuidV7(UUID value) {
        if (value == null || value.version() != 7) {
            throw new IllegalArgumentException("Combat identity must be UUIDv7.");
        }
        return value;
    }

    private String text(JsonNode value) {
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Combat fact has a missing text field.");
        }
        return value.textValue();
    }

    private long nonnegativeLong(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw new IllegalArgumentException("Combat fact has an invalid nonnegative number.");
        }
        return value.longValue();
    }

    private int nonnegativeInt(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw new IllegalArgumentException("Combat fact has an invalid nonnegative integer.");
        }
        return value.intValue();
    }
}
