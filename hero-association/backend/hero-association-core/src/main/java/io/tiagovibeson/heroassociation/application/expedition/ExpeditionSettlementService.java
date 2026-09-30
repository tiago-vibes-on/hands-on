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
import io.tiagovibeson.heroassociation.domain.Item;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.domain.ManagerItem;
import io.tiagovibeson.heroassociation.domain.ManagerRune;
import io.tiagovibeson.heroassociation.domain.Rune;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

/** Applies one frozen Expedition aggregate with the reservation cursor in the same SQL transaction. */
@ApplicationScoped
public class ExpeditionSettlementService {

    private static final int MAX_BODY_BYTES = 131_072;
    private final EntityManager em;
    private final ObjectMapper mapper;

    public ExpeditionSettlementService(EntityManager em, ObjectMapper mapper) {
        this.em = em;
        this.mapper = mapper;
    }

    public enum Result { APPLIED, DUPLICATE }

    @Transactional
    public Result apply(byte[] body, String messageId, String contentType) {
        if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES
                || !"application/json".equals(contentType)) {
            throw new IllegalArgumentException("Expedition settlement must be bounded JSON.");
        }
        JsonNode root = parse(body);
        if (!root.isObject() || root.path("schemaVersion").asInt(-1) != 1) {
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
            hero.applyExpeditionFinal(nonnegativeLong(finalHero.path("experience")),
                    skillPoints(finalHero.path("skillPoints")),
                    nonnegativeInt(finalHero.path("health")), nonnegativeInt(finalHero.path("mana")),
                    nonnegativeLong(finalHero.path("staminaMilliseconds")), now);
        }
        Manager manager = em.find(Manager.class, managerId, LockModeType.PESSIMISTIC_WRITE);
        if (manager == null) {
            throw new IllegalStateException("Reserved Manager is missing.");
        }
        manager.increaseGold(nonnegativeLong(root.path("gold")));
        addItems(manager, root.path("items"));
        addRunes(manager, root.path("runes"));
        reservation.markApplied(digest, now);
        em.flush();
        return Result.APPLIED;
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

    private void addItems(Manager manager, JsonNode node) {
        if (!node.isObject() || node.size() > 128) {
            throw new IllegalArgumentException("Invalid carried item inventory.");
        }
        node.fields().forEachRemaining(entry -> {
            UUID id = uuidText(entry.getKey());
            int quantity = nonnegativeInt(entry.getValue());
            if (quantity == 0) return;
            Item item = em.find(Item.class, id);
            if (item == null) throw new IllegalArgumentException("Unknown carried item.");
            List<ManagerItem> existing = em.createQuery("select i from ManagerItem i "
                            + "where i.manager.id = :manager and i.item.id = :item", ManagerItem.class)
                    .setParameter("manager", manager.getId()).setParameter("item", id)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            if (existing.isEmpty()) em.persist(new ManagerItem(manager, item, quantity));
            else existing.getFirst().increaseQuantity(quantity);
        });
    }

    private void addRunes(Manager manager, JsonNode node) {
        if (!node.isObject() || node.size() > 128) {
            throw new IllegalArgumentException("Invalid carried rune inventory.");
        }
        node.fields().forEachRemaining(entry -> {
            UUID id = uuidText(entry.getKey());
            int quantity = nonnegativeInt(entry.getValue());
            if (quantity == 0) return;
            Rune rune = em.find(Rune.class, id);
            if (rune == null) throw new IllegalArgumentException("Unknown carried rune.");
            List<ManagerRune> existing = em.createQuery("select r from ManagerRune r "
                            + "where r.manager.id = :manager and r.rune.id = :rune", ManagerRune.class)
                    .setParameter("manager", manager.getId()).setParameter("rune", id)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            if (existing.isEmpty()) em.persist(new ManagerRune(manager, rune, quantity));
            else existing.getFirst().increaseQuantity(quantity);
        });
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
            return mapper.readTree(body);
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
        if (id.version() != 7 || !id.toString().equals(text)) {
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
