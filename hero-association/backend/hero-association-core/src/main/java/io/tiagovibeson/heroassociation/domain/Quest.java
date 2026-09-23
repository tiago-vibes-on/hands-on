package io.tiagovibeson.heroassociation.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quest")
public class Quest extends UuidEntity {

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestStatus status;

    @Column(name = "creature_name", nullable = false, length = 100)
    private String creatureName;

    @Column(name = "creatures_defeated", nullable = false)
    private int creaturesDefeated;

    @Column(name = "creatures_required", nullable = false)
    private int creaturesRequired;

    @Column(name = "minimum_heroes", nullable = false)
    private int minimumHeroes;

    @Column(name = "maximum_heroes", nullable = false)
    private int maximumHeroes;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "gold_reward", nullable = false)
    private long goldReward;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "expected_completion_at")
    private Instant expectedCompletionAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", unique = true)
    private Party party;

    @OneToOne(mappedBy = "quest", fetch = FetchType.LAZY)
    private QuestCombat combat;

    protected Quest() {
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public QuestStatus getStatus() {
        return status;
    }

    public String getCreatureName() {
        return creatureName;
    }

    public int getCreaturesDefeated() {
        return creaturesDefeated;
    }

    public int getCreaturesRequired() {
        return creaturesRequired;
    }

    public int getMinimumHeroes() {
        return minimumHeroes;
    }

    public int getMaximumHeroes() {
        return maximumHeroes;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public long getGoldReward() {
        return goldReward;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getExpectedCompletionAt() {
        return expectedCompletionAt;
    }

    public Party getParty() {
        return party;
    }

    public QuestCombat getCombat() {
        return combat;
    }

    public void startWith(Party newParty) {
        status = QuestStatus.IN_PROGRESS;
        party = newParty;
        startedAt = Instant.now();
        expectedCompletionAt = startedAt.plus(durationMinutes, ChronoUnit.MINUTES);
        newParty.assignQuest(this);
    }
}
