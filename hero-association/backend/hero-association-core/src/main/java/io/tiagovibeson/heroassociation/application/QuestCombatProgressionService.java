package io.tiagovibeson.heroassociation.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import io.tiagovibeson.heroassociation.application.combat.QuestCombatSnapshotMapper;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.domain.Quest;
import io.tiagovibeson.heroassociation.domain.QuestCombat;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class QuestCombatProgressionService {

    @Inject
    HeroRepository heroRepository;

    public void synchronize(QuestCombat combat, Instant synchronizedAt) {
        if (combat.getStatus() == CombatStatus.IN_PROGRESS) {
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

        resolveQuest(combat, synchronizedAt);
    }

    private void resolveQuest(QuestCombat combat, Instant synchronizedAt) {
        if (combat.getStatus() == CombatStatus.IN_PROGRESS) {
            return;
        }

        Quest quest = combat.getQuest();
        Party party = quest.getParty();
        if (party == null) {
            return;
        }

        List<Hero> heroes = heroRepository.list("party.id = ?1", party.getId());
        if (quest.resolveFromCombat(combat.getStatus(), synchronizedAt)) {
            heroes.forEach(hero -> hero.changeActivity(HeroActivity.TRAINING));
        }
    }
}
