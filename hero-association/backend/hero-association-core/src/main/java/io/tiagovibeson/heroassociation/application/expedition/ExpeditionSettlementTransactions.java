package io.tiagovibeson.heroassociation.application.expedition;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tiagovibeson.heroassociation.domain.ExpeditionReservation;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

/** Applies one frozen Expedition aggregate with the reservation cursor in the same SQL transaction. */
@ApplicationScoped
public class ExpeditionSettlementTransactions {

    private static final int MAX_BODY_BYTES = 131_072;
    private final EntityManager em;
    private final ObjectMapper mapper;

    public ExpeditionSettlementTransactions(EntityManager em, ObjectMapper mapper) {
        this.em = em;
        this.mapper = mapper;
    }

    public enum Result { APPLIED, DUPLICATE }

    private Result process(byte[] body, String messageId, String contentType, boolean apply) {
        if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES
                || !"application/json".equals(contentType)) {
            throw new IllegalArgumentException("Expedition settlement must be bounded JSON.");
        }
        JsonNode root = parse(body);
        if (!root.isObject() || !root.path("schemaVersion").isIntegralNumber() || root.path("schemaVersion").asInt(-1) != 2) {
            throw new IllegalArgumentException("Unsupported Expedition settlement schema.");
        }
        UUID expeditionId = uuid(root.path("expeditionId"));
        UUID managerId = uuid(root.path("ownerManagerId"));
        UUID agencyId = uuid(root.path("agencyId"));
        UUID partyId = uuid(root.path("partyId"));
        if (!expeditionId.toString().equals(messageId)) {
            throw new IllegalArgumentException("Settlement message ID disagrees with Expedition ID.");
        }
        String digest = digest(body);
        ExpeditionReservation reservation = em.find(ExpeditionReservation.class, expeditionId,
                LockModeType.PESSIMISTIC_WRITE);
        if (reservation == null || reservation.getReleasedAt() != null
                || !managerId.equals(reservation.getOwnerManagerId())
                || !agencyId.equals(reservation.getAgencyId()) || !partyId.equals(reservation.getPartyId())) {
            throw new IllegalStateException("No matching Core Expedition reservation.");
        }
        if (!reservation.isAssetsSnapshotConfirmed()) throw new IllegalStateException("Admission loadout is not confirmed.");
        var questReturn = questReturn(root);
        if (reservation.getWorldPlanJson() == null) throw new IllegalStateException("Admission Map plan is not pinned.");
        try {
            var world = mapper.readValue(reservation.getWorldPlanJson(), io.tiagovibeson.heroassociation.contract.WorldContract.Plan.class);
            if (!questReturn.mapId().equals(world.map().definitionId()) || questReturn.mapVersion() != world.map().version())
                throw new IllegalArgumentException("Settlement Map differs from admission.");
        } catch (IOException invalid) { throw new IllegalStateException("Pinned admission Map is invalid.", invalid); }
        if (reservation.getAppliedAt() != null) {
            if (digest.equals(reservation.getSettlementDigest())) {
                return Result.DUPLICATE;
            }
            throw new IllegalArgumentException("Expedition ID already applied a different aggregate.");
        }
        ExpeditionBaseline baseline = baseline(reservation.getBaselineJson());
        JsonNode finalHeroes = root.path("heroes");
        if (!finalHeroes.isArray() || finalHeroes.size() != baseline.heroes().size()) {
            throw new IllegalArgumentException("Settlement Hero list differs from admission.");
        }
        Set<UUID> seen = new HashSet<>();
        Instant now = Instant.now();
        for (int index = 0; index < baseline.heroes().size(); index++) {
            ExpeditionBaseline.Hero expected = baseline.heroes().get(index);
            JsonNode finalHero = finalHeroes.get(index);
            UUID heroId = uuid(finalHero.path("heroId"));
            if (!heroId.equals(expected.heroId()) || !seen.add(heroId)
                    || !expected.heroClass().name().equals(finalHero.path("heroClass").asText())) {
                throw new IllegalArgumentException("Settlement Hero identity differs from admission.");
            }
            Hero hero = em.find(Hero.class, heroId, LockModeType.PESSIMISTIC_WRITE);
            if (hero == null || hero.getOwnerManager() == null
                    || !managerId.equals(hero.getOwnerManager().getId())
                    || hero.getParty() == null || !partyId.equals(hero.getParty().getId())
                    || hero.getActivity() != HeroActivity.ON_EXPEDITION || !sameBaseline(hero, expected)) {
                throw new IllegalStateException("Core Hero changed after Expedition admission.");
            }
            hero.validateExpeditionFinal(nonnegativeLong(finalHero.path("experience")),
                    skillPoints(finalHero.path("skillPoints")),
                    nonnegativeInt(finalHero.path("health")), nonnegativeInt(finalHero.path("mana")),
                    nonnegativeLong(finalHero.path("staminaMilliseconds")));
            if (apply) hero.applyExpeditionFinal(nonnegativeLong(finalHero.path("experience")), skillPoints(finalHero.path("skillPoints")),
                    nonnegativeInt(finalHero.path("health")), nonnegativeInt(finalHero.path("mana")), nonnegativeLong(finalHero.path("staminaMilliseconds")), now);
        }
        nonnegativeLong(root.path("gold"));
        inventory(root.path("items")); inventory(root.path("runes"));
        if (apply) { reservation.markApplied(digest, now); em.flush(); }
        return Result.APPLIED;
    }

    @Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    public Stage stage(byte[] body, String messageId, String contentType) {
        Result result = process(body, messageId, contentType, false);
        JsonNode root = parse(body);
        UUID expedition = uuid(root.path("expeditionId"));
        ExpeditionReservation reservation = em.find(ExpeditionReservation.class, expedition);
        UUID key = reservation.getAssetsSettlementKey();
        if (result == Result.DUPLICATE) return new Stage(key, true);
        var request = mapper.createObjectNode().put("digest", digest(body)).put("expeditionId", expedition.toString());
        var existing = em.find(io.tiagovibeson.heroassociation.domain.AssetWorkflow.class, key);
        if (existing != null) {
            if (!existing.requestJson.equals(request.toString())) throw new IllegalArgumentException("Settlement key was already staged with another aggregate.");
            return new Stage(key, false);
        }
        var command = io.tiagovibeson.heroassociation.application.assets.AssetCommands.base(mapper, key, "EXPEDITION_CREDIT", reservation.getOwnerManagerId());
        command.put("gold", nonnegativeLong(root.path("gold")));
        command.set("items", mapper.valueToTree(inventory(root.path("items"))));
        command.set("runes", mapper.valueToTree(inventory(root.path("runes"))));
        var workflow = new io.tiagovibeson.heroassociation.domain.AssetWorkflow(key, "EXPEDITION_CREDIT", reservation.getOwnerManagerId(), reservation.getAgencyId(), request.toString(), command.toString());
        workflow.expeditionId = expedition;
        workflow.contextJson = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        em.persist(workflow);
        for (var hero : baseline(reservation.getBaselineJson()).heroes()) {
            Hero current = em.find(Hero.class, hero.heroId(), LockModeType.PESSIMISTIC_WRITE);
            current.fenceAssets(key);
        }
        em.flush(); return new Stage(key, false);
    }
    /** Joins workflow completion: Hero progress, the applied cursor, and workflow state commit together. */
    @Transactional
    public void finish(io.tiagovibeson.heroassociation.domain.AssetWorkflow workflow, JsonNode receipt) {
        ExpeditionReservation reservation = em.find(ExpeditionReservation.class, workflow.expeditionId, LockModeType.PESSIMISTIC_WRITE);
        if (reservation == null || !workflow.id.equals(reservation.getAssetsSettlementKey())) throw new io.tiagovibeson.heroassociation.application.assets.AssetCommands.ProtocolConflict("Settlement cursor differs from workflow.");
        for (var hero : baseline(reservation.getBaselineJson()).heroes()) {
            Hero current = em.find(Hero.class, hero.heroId(), LockModeType.PESSIMISTIC_WRITE);
            if (current == null || !workflow.id.equals(current.getPendingAssetOperation())) throw new io.tiagovibeson.heroassociation.application.assets.AssetCommands.ProtocolConflict("Settlement Hero fence differs from workflow.");
            current.finishAssets(workflow.id);
        }
        process(workflow.contextJson.getBytes(java.nio.charset.StandardCharsets.UTF_8), workflow.expeditionId.toString(), "application/json", true);
    }
    public record Stage(UUID operationKey, boolean duplicate) { }

    /** Validate every permanent-owner input before any remote Quest payout is requested. */
    @Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    public io.tiagovibeson.heroassociation.contract.QuestContract.ReturnRequest validate(byte[] body, String messageId, String contentType) {
        process(body, messageId, contentType, false);
        return questReturn(parse(body));
    }
    private io.tiagovibeson.heroassociation.contract.QuestContract.ReturnRequest questReturn(JsonNode root) {
        if (!root.path("mapVersion").isIntegralNumber() || !root.path("mapVersion").canConvertToInt() || !root.has("quest"))
            throw new IllegalArgumentException("Settlement Map version and optional Quest pin are required.");
        var progress = root.path("quest").isNull() ? null : mapper.convertValue(root.path("quest"), io.tiagovibeson.heroassociation.contract.QuestContract.Progress.class);
        return new io.tiagovibeson.heroassociation.contract.QuestContract.ReturnRequest(uuid(root.path("expeditionId")), uuid(root.path("ownerManagerId")),
                uuid(root.path("agencyId")), uuid(root.path("mapId")), root.path("mapVersion").intValue(), progress);
    }

    private boolean sameBaseline(Hero hero, ExpeditionBaseline.Hero expected) {
        if (hero.getHeroClass() != expected.heroClass() || hero.getExperience() != expected.experience()
                || hero.getCurrentHealth() != expected.health() || hero.getCurrentMana() != expected.mana()
                || hero.getStaminaMilliseconds() != expected.staminaMilliseconds()) {
            return false;
        }
        for (HeroSkill skill : HeroSkill.values()) {
            if (hero.getSkillPoints(skill).compareTo(expected.skillPoints().get(skill)) != 0) {
                return false;
            }
        }
        return true;
    }

    private Map<HeroSkill, BigDecimal> skillPoints(JsonNode node) {
        if (!node.isObject() || node.size() != HeroSkill.values().length) {
            throw new IllegalArgumentException("Final Hero skill totals are incomplete.");
        }
        Map<HeroSkill, BigDecimal> result = new EnumMap<>(HeroSkill.class);
        for (HeroSkill skill : HeroSkill.values()) {
            JsonNode value = node.path(skill.name());
            if (!value.isNumber()) {
                throw new IllegalArgumentException("Final Hero skill total is not numeric.");
            }
            BigDecimal points = value.decimalValue();
            if (points.signum() < 0 || points.scale() > 6) {
                throw new IllegalArgumentException("Final Hero skill total is invalid.");
            }
            result.put(skill, points);
        }
        return result;
    }

    private java.util.Map<UUID, Integer> inventory(JsonNode node) {
        if (!node.isObject() || node.size() > 128) throw new IllegalArgumentException("Carried inventory is invalid.");
        java.util.Map<UUID, Integer> result = new java.util.TreeMap<>();
        node.fields().forEachRemaining(entry -> result.put(uuidText(entry.getKey()), nonnegativeInt(entry.getValue()))); return result;
    }

    private ExpeditionBaseline baseline(String json) {
        try {
            return mapper.readValue(json, ExpeditionBaseline.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Stored Expedition baseline is invalid.", exception);
        }
    }

    private JsonNode parse(byte[] body) {
        try {
            String text = java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(body)).toString();
            return mapper.readTree(text);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Expedition settlement JSON is invalid.", exception);
        }
    }

    private String digest(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable.", exception);
        }
    }

    private UUID uuid(JsonNode node) {
        if (!node.isTextual()) throw new IllegalArgumentException("Missing UUIDv7.");
        return uuidText(node.textValue());
    }

    private UUID uuidText(String text) {
        UUID id = UUID.fromString(text);
        if (id.version() != 7 || id.variant() != 2 || !id.toString().equals(text)) {
            throw new IllegalArgumentException("Settlement IDs must be UUIDv7.");
        }
        return id;
    }

    private long nonnegativeLong(JsonNode node) {
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0) {
            throw new IllegalArgumentException("Settlement integer is invalid.");
        }
        return node.longValue();
    }

    private int nonnegativeInt(JsonNode node) {
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < 0) {
            throw new IllegalArgumentException("Settlement quantity is invalid.");
        }
        return node.intValue();
    }
}
