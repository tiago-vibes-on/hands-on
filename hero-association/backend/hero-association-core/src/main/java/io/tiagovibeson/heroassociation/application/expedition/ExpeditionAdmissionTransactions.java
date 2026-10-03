package io.tiagovibeson.heroassociation.application.expedition;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tiagovibeson.heroassociation.domain.ExpeditionReservation;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.RuneEffect;
import io.tiagovibeson.heroassociation.domain.Party;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

/** Private Core entry reservation. No browser or Expedition HTTP caller is connected yet. */
@ApplicationScoped
public class ExpeditionAdmissionTransactions {

    private final EntityManager em;
    private final ObjectMapper mapper;

    public ExpeditionAdmissionTransactions(EntityManager em, ObjectMapper mapper) {
        this.em = em;
        this.mapper = mapper;
    }

    @Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    public ExpeditionBaseline reserve(UUID expeditionId, UUID managerId, UUID agencyId, UUID partyId) {
        return reserve(expeditionId, managerId, agencyId, partyId, null);
    }

    @Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    public ExpeditionBaseline reserve(UUID expeditionId, UUID managerId, UUID agencyId, UUID partyId,
                                     io.tiagovibeson.heroassociation.contract.WorldContract.Plan plan) {
        if (expeditionId == null || expeditionId.version() != 7 || managerId == null || managerId.version() != 7
                || agencyId == null || agencyId.version() != 7 || partyId == null || partyId.version() != 7) {
            throw new IllegalArgumentException("Expedition admission requires UUIDv7 IDs.");
        }
        ExpeditionReservation existing = em.find(ExpeditionReservation.class, expeditionId,
                LockModeType.PESSIMISTIC_WRITE);
        if (existing != null) {
            if (!managerId.equals(existing.getOwnerManagerId()) || !agencyId.equals(existing.getAgencyId())
                    || !partyId.equals(existing.getPartyId())) {
                throw new IllegalArgumentException("Expedition ID belongs to a different reservation.");
            }
            if (existing.getAppliedAt() != null || existing.getReleasedAt() != null) {
                throw new ReservationClosedException();
            }
            return decode(existing.getBaselineJson());
        }
        Party party = em.find(Party.class, partyId, LockModeType.PESSIMISTIC_WRITE);
        if (party == null || !party.getOwnerManager().getId().equals(managerId)
                || !party.getAgency().getId().equals(agencyId)) {
            throw new IllegalArgumentException("Party is not available to this Manager for Expedition.");
        }
        Long membership = em.createQuery("select count(m) from AgencyMember m "
                        + "where m.agency.id = :agency and m.manager.id = :manager", Long.class)
                .setParameter("agency", agencyId).setParameter("manager", managerId).getSingleResult();
        if (membership != 1) {
            throw new IllegalArgumentException("Manager is not a member of this agency.");
        }

        List<Hero> heroes = em.createQuery("select h from Hero h where h.party.id = :party order by h.id", Hero.class)
                .setParameter("party", partyId).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        if (heroes.isEmpty() || heroes.size() > 4 || heroes.stream().noneMatch(hero -> hero.getCurrentHealth() > 0)) {
            throw new IllegalArgumentException("Expedition needs one to four Heroes, at least one living.");
        }
        Instant now = Instant.now();
        Map<UUID, HeroActivity> previousActivities = new java.util.HashMap<>();
        for (Hero hero : heroes) {
            hero.requireNoPendingAssets();
            if (hero.getOwnerManager() == null || !hero.getOwnerManager().getId().equals(managerId)
                    || hero.getActivity() == HeroActivity.ON_EXPEDITION) {
                throw new IllegalArgumentException("All reserved Heroes must be available personal Heroes.");
            }
            previousActivities.put(hero.getId(), hero.getActivity());
            hero.changeActivity(HeroActivity.ON_EXPEDITION, party.getAgency().getRestLevel());
        }
        ExpeditionBaseline baseline = new ExpeditionBaseline(heroes.stream()
                .map(hero -> snapshot(hero, previousActivities.get(hero.getId()))).toList());
        var reservation = new ExpeditionReservation(expeditionId, managerId, agencyId, partyId, encode(baseline), now);
        if (plan != null) {
            try { reservation.pinWorldPlan(mapper.writeValueAsString(plan)); }
            catch (JsonProcessingException invalid) { throw new IllegalStateException("Could not pin Map plan.", invalid); }
        }
        em.persist(reservation);
        em.flush();
        return baseline;
    }

    /** Candidates are only hints; Expedition must prove absence in healthy Redis before release. */
    public List<ReservationCandidate> orphanCandidates(Instant olderThan, int limit) {
        if (olderThan == null || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid orphan scan bounds.");
        }
        return em.createQuery("select r from ExpeditionReservation r where r.appliedAt is null "
                        + "and r.releasedAt is null and r.reservedAt < :olderThan order by r.reservedAt",
                        ExpeditionReservation.class)
                .setParameter("olderThan", olderThan).setMaxResults(limit).getResultList().stream()
                .map(r -> new ReservationCandidate(r.getExpeditionId(), r.getOwnerManagerId()))
                .toList();
    }

    public record ReservationCandidate(UUID expeditionId, UUID ownerManagerId) { }

    /** Call only after Expedition has positively confirmed that its Redis run does not exist. */
    @Transactional
    public boolean releaseProvenAbsent(UUID expeditionId, UUID managerId) {
        if (expeditionId == null || managerId == null) {
            throw new IllegalArgumentException("Reservation identity is required.");
        }
        ExpeditionReservation reservation = em.find(ExpeditionReservation.class, expeditionId,
                LockModeType.PESSIMISTIC_WRITE);
        if (reservation == null) {
            return false;
        }
        if (!managerId.equals(reservation.getOwnerManagerId())) {
            throw new IllegalArgumentException("Reservation belongs to a different Manager.");
        }
        if (reservation.getReleasedAt() != null) {
            return false;
        }
        if (reservation.getAppliedAt() != null) {
            throw new IllegalStateException("A settled Expedition reservation cannot be released.");
        }
        ExpeditionBaseline baseline = decode(reservation.getBaselineJson());
        for (ExpeditionBaseline.Hero expected : baseline.heroes()) {
            Hero hero = em.find(Hero.class, expected.heroId(), LockModeType.PESSIMISTIC_WRITE);
            if (hero == null || hero.getActivity() != HeroActivity.ON_EXPEDITION
                    || hero.getParty() == null || !reservation.getPartyId().equals(hero.getParty().getId())
                    || hero.getOwnerManager() == null || !managerId.equals(hero.getOwnerManager().getId())
                    || hero.getExperience() != expected.experience()
                    || hero.getCurrentHealth() != expected.health()
                    || hero.getCurrentMana() != expected.mana()
                    || hero.getStaminaMilliseconds() != expected.staminaMilliseconds()) {
                throw new IllegalStateException("Reserved Hero changed; refusing orphan release.");
            }
            for (HeroSkill skill : HeroSkill.values()) {
                if (hero.getSkillPoints(skill).compareTo(expected.skillPoints().get(skill)) != 0) {
                    throw new IllegalStateException("Reserved Hero skills changed; refusing orphan release.");
                }
            }
            hero.changeActivity(expected.previousActivity());
        }
        reservation.markReleased(Instant.now());
        em.flush();
        return true;
    }

    private ExpeditionBaseline.Hero snapshot(Hero hero, HeroActivity previousActivity) {
        Map<HeroSkill, java.math.BigDecimal> points = new EnumMap<>(HeroSkill.class);
        for (HeroSkill skill : HeroSkill.values()) {
            points.put(skill, hero.getSkillPoints(skill));
        }
        Map<Integer, UUID> runeIds = new java.util.HashMap<>();
        double criticalChance = 0;
        double criticalDamageBonus = 0;
        for (var slot : hero.getRuneSlots()) {
            var rune = slot.getRune();
            runeIds.put(slot.getSlotIndex(), rune.getId());
            if (rune.getEffect() == RuneEffect.CRITICAL_CHANCE) {
                criticalChance += rune.getEffectValue();
            } else if (rune.getEffect() == RuneEffect.CRITICAL_DAMAGE) {
                criticalDamageBonus += rune.getEffectValue();
            }
        }
        return new ExpeditionBaseline.Hero(hero.getId(), hero.getName(), hero.getHeroClass(),
                hero.getExperience(), points, hero.getCurrentHealth(), hero.getCurrentMana(),
                hero.getStaminaMilliseconds(), previousActivity, runeIds,
                Math.min(1, criticalChance), 2 + criticalDamageBonus);
    }

    private String encode(ExpeditionBaseline baseline) {
        try {
            return mapper.writeValueAsString(baseline);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not encode Core Expedition baseline.", exception);
        }
    }

    private ExpeditionBaseline decode(String json) {
        try {
            return mapper.readValue(json, ExpeditionBaseline.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not decode Core Expedition baseline.", exception);
        }
    }
    @Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    public AdmissionClaim claim(java.util.UUID expeditionId) {
        ExpeditionReservation reservation = em.find(ExpeditionReservation.class, expeditionId);
        if (reservation == null || reservation.getAppliedAt() != null || reservation.getReleasedAt() != null) throw new IllegalStateException("Expedition reservation is no longer active.");
        return new AdmissionClaim(reservation.getAssetsAdmissionKey(), reservation.getOwnerManagerId(), decode(reservation.getBaselineJson()), reservation.isAssetsSnapshotConfirmed());
    }
    @Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    public ExpeditionBaseline complete(java.util.UUID expeditionId, com.fasterxml.jackson.databind.JsonNode receipt) {
        ExpeditionReservation reservation = em.find(ExpeditionReservation.class, expeditionId, LockModeType.PESSIMISTIC_WRITE);
        if (reservation == null || reservation.getAppliedAt() != null || reservation.getReleasedAt() != null) throw new IllegalStateException("Expedition reservation is no longer active.");
        if (reservation.isAssetsSnapshotConfirmed()) return decode(reservation.getBaselineJson());
        ExpeditionBaseline baseline = decode(reservation.getBaselineJson());
        var slots = mapper.convertValue(receipt.path("heroes"), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<java.util.UUID, java.util.List<io.tiagovibeson.heroassociation.application.assets.AssetsClient.RuneSlot>>>() {});
        if (!slots.keySet().equals(new java.util.HashSet<>(baseline.heroes().stream().map(ExpeditionBaseline.Hero::heroId).toList()))) throw new IllegalStateException("Assets omitted a reserved Hero loadout.");
        ExpeditionBaseline finalBaseline = new ExpeditionBaseline(baseline.heroes().stream().map(hero -> {
            java.util.Map<Integer, java.util.UUID> runeIds = new java.util.HashMap<>();
            double chance = 0; double damage = 0;
            var heroSlots = slots.get(hero.heroId());
            if (heroSlots == null || heroSlots.size() > 5) throw new IllegalStateException("Assets loadout is invalid.");
            for (var slot : heroSlots) {
                if (slot.slotIndex() < 0 || slot.slotIndex() >= 5 || runeIds.put(slot.slotIndex(), slot.rune().id()) != null || !Double.isFinite(slot.rune().effectValue()) || slot.rune().effectValue() < 0) throw new IllegalStateException("Assets loadout is invalid.");
                if ("CRITICAL_CHANCE".equals(slot.rune().effect())) chance += slot.rune().effectValue();
                if ("CRITICAL_DAMAGE".equals(slot.rune().effect())) damage += slot.rune().effectValue();
            }
            return new ExpeditionBaseline.Hero(hero.heroId(), hero.name(), hero.heroClass(), hero.experience(), hero.skillPoints(), hero.health(), hero.mana(), hero.staminaMilliseconds(), hero.previousActivity(), runeIds, Math.min(1, chance), 2 + damage);
        }).toList());
        reservation.confirmAssetsSnapshot(encode(finalBaseline)); em.flush(); return finalBaseline;
    }
    public record AdmissionClaim(java.util.UUID commandKey, java.util.UUID managerId, ExpeditionBaseline baseline, boolean confirmed) { }

    @Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    public io.tiagovibeson.heroassociation.contract.WorldContract.Plan worldPlan(UUID expeditionId, UUID managerId, UUID agencyId, UUID partyId, UUID mapId) {
        var reservation = em.find(ExpeditionReservation.class, expeditionId);
        if (reservation == null) return null;
        if (!managerId.equals(reservation.getOwnerManagerId()) || !agencyId.equals(reservation.getAgencyId())
                || !partyId.equals(reservation.getPartyId()) || reservation.getWorldPlanJson() == null)
            throw new IllegalArgumentException("Expedition ID belongs to a different admission.");
        try {
            var plan = mapper.readValue(reservation.getWorldPlanJson(), io.tiagovibeson.heroassociation.contract.WorldContract.Plan.class);
            if (!mapId.equals(plan.map().definitionId())) throw new IllegalArgumentException("Expedition ID already pinned another Map.");
            return plan;
        } catch (JsonProcessingException invalid) { throw new IllegalStateException("Pinned Map is invalid.", invalid); }
    }

}
