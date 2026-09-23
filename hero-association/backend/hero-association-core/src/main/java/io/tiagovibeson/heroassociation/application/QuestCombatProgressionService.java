package io.tiagovibeson.heroassociation.application;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import io.tiagovibeson.heroassociation.application.combat.QuestCombatSnapshotMapper;
import io.tiagovibeson.heroassociation.domain.QuestCombat;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QuestCombatProgressionService {

    public void synchronize(QuestCombat combat, Instant synchronizedAt) {
        if (combat.getStatus() != CombatStatus.IN_PROGRESS) {
            return;
        }

        long elapsedMilliseconds = Math.max(0, Duration.between(combat.getLastSynchronizedAt(), synchronizedAt).toMillis());
        if (elapsedMilliseconds == 0) {
            return;
        }

        var battle = QuestCombatSnapshotMapper.toBattle(combat);
        var events = battle.advanceTo(
                combat.getCurrentTimeMilliseconds() + elapsedMilliseconds,
                ThreadLocalRandom.current()::nextDouble);
        QuestCombatSnapshotMapper.apply(combat, battle, synchronizedAt);
        combat.appendEvents(events);
    }
}
