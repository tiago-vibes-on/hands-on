package io.tiagovibeson.heroassociation.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
    private int level;

    @Column(name = "magic_level", nullable = false)
    private int magicLevel;

    @Column(name = "current_health", nullable = false)
    private int currentHealth;

    @Column(name = "current_mana", nullable = false)
    private int currentMana;

    @Column(nullable = false)
    private int stamina;

    @Column(name = "last_resource_synchronized_at", nullable = false)
    private Instant lastResourceSynchronizedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HeroActivity activity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id")
    private Party party;

    @OneToMany(mappedBy = "hero", fetch = FetchType.LAZY)
    @OrderBy("slotIndex")
    private List<HeroRune> runeSlots = new ArrayList<>();

    protected Hero() {
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
        return level;
    }

    public int getMagicLevel() {
        return magicLevel;
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public int getCurrentMana() {
        return currentMana;
    }

    public int getStamina() {
        return stamina;
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
                heroClass.getBaseHealth(),
                heroClass.getHealthRecoveryPerSecond() * recoveryMultiplier,
                elapsedSeconds);
        currentMana = recover(
                currentMana,
                heroClass.getBaseMana(),
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
