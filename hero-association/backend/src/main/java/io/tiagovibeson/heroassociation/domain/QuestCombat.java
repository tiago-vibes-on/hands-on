package io.tiagovibeson.heroassociation.domain;

import java.util.ArrayList;
import java.util.List;

import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "quest_combat")
public class QuestCombat extends UuidEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quest_id", nullable = false, unique = true)
    private Quest quest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CombatStatus status;

    @Column(name = "current_time_milliseconds", nullable = false)
    private long currentTimeMilliseconds;

    @OneToMany(mappedBy = "combat", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("formationIndex")
    private List<QuestCombatant> combatants = new ArrayList<>();

    protected QuestCombat() {
    }

    public Quest getQuest() {
        return quest;
    }

    public CombatStatus getStatus() {
        return status;
    }

    public long getCurrentTimeMilliseconds() {
        return currentTimeMilliseconds;
    }

    public List<QuestCombatant> getCombatants() {
        return combatants;
    }
}
