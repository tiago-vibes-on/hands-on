package io.tiagovibeson.heroassociation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "hero")
public class Hero extends UuidEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String alias;

    @Enumerated(EnumType.STRING)
    @Column(name = "hero_class", nullable = false, length = 20)
    private HeroClass heroClass;

    @Column(nullable = false)
    private long experience;

    @Column(name = "melee_points", nullable = false, precision = 20, scale = 6)
    private BigDecimal meleePoints = BigDecimal.ZERO;

    @Column(name = "distance_points", nullable = false, precision = 20, scale = 6)
    private BigDecimal distancePoints = BigDecimal.ZERO;

    @Column(name = "magic_points", nullable = false, precision = 20, scale = 6)
    private BigDecimal magicPoints = BigDecimal.ZERO;

    @Column(name = "shield_points", nullable = false, precision = 20, scale = 6)
    private BigDecimal shieldPoints = BigDecimal.ZERO;

    @Column(name = "current_health", nullable = false)
    private int currentHealth;

    @Column(name = "current_mana", nullable = false)
    private int currentMana;

    @Column(name = "stamina_milliseconds", nullable = false)
    private long staminaMilliseconds;

    @Column(name = "last_resource_synchronized_at", nullable = false)
    private Instant lastResourceSynchronizedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HeroActivity activity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id")
    private Agency agency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id")
    private Party party;

    @OneToMany(mappedBy = "hero", fetch = FetchType.LAZY)
    @OrderBy("slotIndex")
    private List<HeroRune> runeSlots = new ArrayList<>();

    protected Hero() {
    }

    public static Hero createRecruitable(String name, String alias, HeroClass heroClass) {
        Hero hero = new Hero();
        hero.name = name;
        hero.alias = alias;
        hero.heroClass = heroClass;
        hero.experience = 0;
        hero.currentHealth = heroClass.getBaseHealth();
        hero.currentMana = heroClass.getBaseMana();
        hero.staminaMilliseconds = HeroProgression.MAX_STAMINA_MILLISECONDS;
        hero.activity = HeroActivity.TRAINING;
        return hero;
    }

    @PrePersist
    void initializeResourceSynchronization() {
        if (lastResourceSynchronizedAt == null) {
            lastResourceSynchronizedAt = Instant.now();
        }
    }

    public String getName() {
        return name;
    }

    public String getAlias() {
        return alias;
    }

    public HeroClass getHeroClass() {
        return heroClass;
    }

    public int getLevel() {
        return HeroProgression.levelForExperience(experience);
    }

    public long getExperience() {
        return experience;
    }

    public int getMagicLevel() {
        return getSkillLevel(HeroSkill.MAGIC);
    }

    public int getSkillLevel(HeroSkill skill) {
        return HeroProgression.skillLevelForPoints(getSkillPoints(skill));
    }

    public BigDecimal getSkillPoints(HeroSkill skill) {
        return switch (Objects.requireNonNull(skill)) {
            case MELEE -> meleePoints;
            case DISTANCE -> distancePoints;
            case MAGIC -> magicPoints;
            case SHIELD -> shieldPoints;
        };
    }

    public void addExperience(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Experience award cannot be negative.");
        }
        experience = Math.addExact(experience, amount);
    }

    public void addSkillPoints(HeroSkill skill, BigDecimal amount) {
        Objects.requireNonNull(skill);
        BigDecimal increment = Objects.requireNonNull(amount).setScale(6, RoundingMode.UNNECESSARY);
        if (increment.signum() < 0) {
            throw new IllegalArgumentException("Skill point award cannot be negative.");
        }
        switch (skill) {
            case MELEE -> meleePoints = meleePoints.add(increment);
            case DISTANCE -> distancePoints = distancePoints.add(increment);
            case MAGIC -> magicPoints = magicPoints.add(increment);
            case SHIELD -> shieldPoints = shieldPoints.add(increment);
        }
    }

    public int getMaxHealth() {
        return Math.toIntExact((long) heroClass.getBaseHealth()
                + (long) (getLevel() - 1) * heroClass.getHealthGainPerLevel());
    }

    public int getMaxMana() {
        return Math.toIntExact((long) heroClass.getBaseMana()
                + (long) (getLevel() - 1) * heroClass.getManaGainPerLevel());
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public int getCurrentMana() {
        return currentMana;
    }

    public int getStamina() {
        return Math.toIntExact(staminaMilliseconds * 100
                / HeroProgression.MAX_STAMINA_MILLISECONDS);
    }

    public long getStaminaMilliseconds() {
        return staminaMilliseconds;
    }

    public void consumeStaminaMilliseconds(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Stamina consumption cannot be negative.");
        }
        staminaMilliseconds = Math.max(0, staminaMilliseconds
                - Math.min(amount, HeroProgression.MAX_STAMINA_MILLISECONDS));
    }

    public void recoverStaminaMilliseconds(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Stamina recovery cannot be negative.");
        }
        staminaMilliseconds = Math.min(HeroProgression.MAX_STAMINA_MILLISECONDS,
                staminaMilliseconds + Math.min(amount, HeroProgression.MAX_STAMINA_MILLISECONDS));
    }

    public HeroActivity getActivity() {
        return activity;
    }

    public void changeActivity(HeroActivity newActivity) {
        Instant changedAt = Instant.now();
        recoverAgencyResourcesAt(changedAt);
        activity = newActivity;
        lastResourceSynchronizedAt = changedAt;
    }

    public void synchronizeCombatResources(int health, int mana, Instant synchronizedAt) {
        currentHealth = health;
        currentMana = mana;
        lastResourceSynchronizedAt = synchronizedAt;
    }

    public void recoverAgencyResourcesAt(Instant synchronizedAt) {
        if (activity == HeroActivity.ON_QUEST || !synchronizedAt.isAfter(lastResourceSynchronizedAt)) {
            return;
        }

        long elapsedSeconds = Duration.between(lastResourceSynchronizedAt, synchronizedAt).toSeconds();
        if (elapsedSeconds == 0) {
            return;
        }

        int recoveryMultiplier = activity == HeroActivity.RESTING ? 2 : 1;
        currentHealth = recover(
                currentHealth,
                getMaxHealth(),
                heroClass.getHealthRecoveryPerSecond() * recoveryMultiplier,
                elapsedSeconds);
        currentMana = recover(
                currentMana,
                getMaxMana(),
                heroClass.getManaRecoveryPerSecond() * recoveryMultiplier,
                elapsedSeconds);
        lastResourceSynchronizedAt = lastResourceSynchronizedAt.plusSeconds(elapsedSeconds);
    }

    private int recover(int currentValue, int maximumValue, int recoveryPerSecond, long elapsedSeconds) {
        long missingAmount = maximumValue - currentValue;
        if (missingAmount <= 0) {
            return maximumValue;
        }

        long secondsUntilMaximum = (missingAmount + recoveryPerSecond - 1) / recoveryPerSecond;
        if (elapsedSeconds >= secondsUntilMaximum) {
            return maximumValue;
        }

        return currentValue + Math.toIntExact(elapsedSeconds * recoveryPerSecond);
    }

    public boolean isRecruitable() {
        return agency == null;
    }

    public void recruitTo(Agency newAgency) {
        if (!isRecruitable()) {
            throw new IllegalStateException("A hero who belongs to an agency cannot be recruited again.");
        }

        agency = newAgency;
        party = null;
        activity = HeroActivity.TRAINING;
        currentHealth = getMaxHealth();
        currentMana = getMaxMana();
        staminaMilliseconds = HeroProgression.MAX_STAMINA_MILLISECONDS;
        lastResourceSynchronizedAt = Instant.now();
    }

    public Party getParty() {
        return party;
    }

    public void assignToParty(Party newParty) {
        party = newParty;
    }

    public void removeFromParty() {
        party = null;
    }

    public List<HeroRune> getRuneSlots() {
        return runeSlots;
    }
}
