package io.tiagovibeson.heroassociation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quest")
public class Quest extends UuidEntity {

    @Column(nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestStatus status;

    @Column(name = "creature_name", nullable = false, length = 100)
    private String creatureName;

    @Column(name = "creatures_defeated", nullable = false)
    private int creaturesDefeated;

    @Column(name = "creatures_required", nullable = false)
    private int creaturesRequired;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false, unique = true)
    private Party party;

    protected Quest() {
    }

    public String getTitle() {
        return title;
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
}
