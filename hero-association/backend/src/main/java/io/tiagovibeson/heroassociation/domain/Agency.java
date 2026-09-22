package io.tiagovibeson.heroassociation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "agency")
public class Agency extends UuidEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leader_id", nullable = false)
    private Manager leader;

    @Column(nullable = false)
    private long gold;

    @Column(nullable = false)
    private int reputation;

    @Column(name = "agency_level", nullable = false)
    private int agencyLevel;

    @Column(name = "training_level", nullable = false)
    private int trainingLevel;

    @Column(name = "rest_level", nullable = false)
    private int restLevel;

    @Column(name = "size_level", nullable = false)
    private int sizeLevel;

    @Column(name = "reputation_level", nullable = false)
    private int reputationLevel;

    @Column(name = "intelligence_level", nullable = false)
    private int intelligenceLevel;

    protected Agency() {
    }

    public String getName() {
        return name;
    }

    public Manager getLeader() {
        return leader;
    }

    public long getGold() {
        return gold;
    }

    public int getReputation() {
        return reputation;
    }

    public int getAgencyLevel() {
        return agencyLevel;
    }

    public int getTrainingLevel() {
        return trainingLevel;
    }

    public int getRestLevel() {
        return restLevel;
    }

    public int getSizeLevel() {
        return sizeLevel;
    }

    public int getReputationLevel() {
        return reputationLevel;
    }

    public int getIntelligenceLevel() {
        return intelligenceLevel;
    }
}
