package io.tiagovibeson.heroassociation.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.tiagovibeson.heroassociation.domain.combat.CombatBattleSnapshot;
import io.tiagovibeson.heroassociation.domain.combat.CombatEvent;
import io.tiagovibeson.heroassociation.domain.combat.CombatantSnapshot;
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

    private static final int EVENT_HISTORY_LIMIT = 100;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quest_id", nullable = false, unique = true)
    private Quest quest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CombatStatus status;

    @Column(name = "current_time_milliseconds", nullable = false)
    private long currentTimeMilliseconds;

    @Column(name = "next_recovery_at", nullable = false)
    private long nextRecoveryAt;

    @Column(name = "last_synchronized_at", nullable = false)
    private Instant lastSynchronizedAt;

    @Column(name = "next_event_sequence", nullable = false)
    private long nextEventSequence;

    @OneToMany(mappedBy = "combat", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("formationIndex")
    private List<QuestCombatant> combatants = new ArrayList<>();

    @OneToMany(mappedBy = "combat", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequenceNumber desc")
    private List<QuestCombatEvent> events = new ArrayList<>();

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

    public long getNextRecoveryAt() {
        return nextRecoveryAt;
    }

    public Instant getLastSynchronizedAt() {
        return lastSynchronizedAt;
    }

    public List<QuestCombatant> getCombatants() {
        return combatants;
    }

    public List<QuestCombatEvent> getEvents() {
        return events.stream()
                .sorted(Comparator.comparingLong(QuestCombatEvent::getSequenceNumber).reversed())
                .toList();
    }

    public void apply(CombatBattleSnapshot snapshot, Instant synchronizedAt) {
        status = snapshot.status();
        currentTimeMilliseconds = snapshot.currentTimeMilliseconds();
        nextRecoveryAt = snapshot.nextRecoveryAt();
        lastSynchronizedAt = synchronizedAt;

        Map<UUID, QuestCombatant> combatantsById = combatants.stream()
                .collect(Collectors.toMap(QuestCombatant::getId, Function.identity()));
        allCombatants(snapshot).forEach(combatantSnapshot -> {
            UUID combatantId = UUID.fromString(combatantSnapshot.id());
            QuestCombatant combatant = combatantsById.get(combatantId);
            if (combatant == null) {
                throw new IllegalArgumentException("Combat snapshot contains an unknown combatant: " + combatantId);
            }
            combatant.apply(combatantSnapshot, synchronizedAt);
        });
    }

    public void appendEvents(List<CombatEvent> newEvents) {
        if (newEvents.isEmpty()) {
            return;
        }

        Map<UUID, QuestCombatant> combatantsById = combatants.stream()
                .collect(Collectors.toMap(QuestCombatant::getId, Function.identity()));
        newEvents.forEach(event -> events.add(QuestCombatEvent.from(
                this,
                nextEventSequence++,
                event,
                combatantsById)));

        while (events.size() > EVENT_HISTORY_LIMIT) {
            events.stream()
                    .min(Comparator.comparingLong(QuestCombatEvent::getSequenceNumber))
                    .ifPresent(events::remove);
        }
    }

    private static List<CombatantSnapshot> allCombatants(CombatBattleSnapshot snapshot) {
        List<CombatantSnapshot> combatants = new ArrayList<>(snapshot.heroes());
        combatants.addAll(snapshot.creatures());
        return combatants;
    }
}
