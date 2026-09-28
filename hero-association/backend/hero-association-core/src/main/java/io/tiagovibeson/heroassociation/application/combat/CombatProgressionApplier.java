package io.tiagovibeson.heroassociation.application.combat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroProgression;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.QuestCombat;
import io.tiagovibeson.heroassociation.domain.QuestCombatant;
import io.tiagovibeson.heroassociation.domain.combat.CombatAction;
import io.tiagovibeson.heroassociation.domain.combat.CombatEvent;
import io.tiagovibeson.heroassociation.domain.combat.CombatHit;
import io.tiagovibeson.heroassociation.domain.combat.CombatStatus;
import io.tiagovibeson.heroassociation.domain.combat.CombatTeam;

public final class CombatProgressionApplier {

    private static final BigDecimal MAGIC_POINTS_PER_MANA = new BigDecimal("0.05");

    private CombatProgressionApplier() {
    }

    public static void apply(
            QuestCombat combat, List<CombatEvent> events, CombatStatus finalStatus, long targetTimeMilliseconds) {
        Map<String, QuestCombatant> combatants = new HashMap<>();
        Map<String, Hero> livingHeroes = new HashMap<>();
        for (QuestCombatant combatant : combat.getCombatants()) {
            String id = combatant.getId().toString();
            combatants.put(id, combatant);
            if (combatant.getHero() != null && combatant.getCurrentHealth() > 0) {
                livingHeroes.put(id, combatant.getHero());
            }
        }

        long processedAt = combat.getCurrentTimeMilliseconds();
        long activeUntil = finalStatus == CombatStatus.IN_PROGRESS
                ? targetTimeMilliseconds
                : events.getLast().occurredAtMilliseconds();
        for (CombatEvent event : events) {
            if (event.occurredAtMilliseconds() < processedAt || event.occurredAtMilliseconds() > activeUntil) {
                throw new IllegalArgumentException("Combat events must be ordered within the active battle interval.");
            }
            consumeActiveStamina(livingHeroes, event.occurredAtMilliseconds() - processedAt);
            processedAt = event.occurredAtMilliseconds();
            awardActionSkills(event, combatants);
            for (CombatHit hit : event.hits()) {
                if (!hit.defeated()) {
                    continue;
                }
                QuestCombatant target = combatants.get(hit.targetId());
                if (target == null) {
                    throw new IllegalArgumentException("Combat hit has an unknown target.");
                }
                if (target.getTeam() == CombatTeam.CREATURES) {
                    for (Hero hero : livingHeroes.values()) {
                        hero.addExperience(HeroProgression.creatureExperienceAward(
                                target.getBaseExperience(), hero.getStaminaMilliseconds()));
                    }
                } else {
                    livingHeroes.remove(hit.targetId());
                }
            }
        }
        consumeActiveStamina(livingHeroes, activeUntil - processedAt);
    }

    private static void consumeActiveStamina(Map<String, Hero> livingHeroes, long elapsedMilliseconds) {
        if (elapsedMilliseconds <= 0) {
            return;
        }
        livingHeroes.values().forEach(hero -> hero.consumeStaminaMilliseconds(elapsedMilliseconds));
    }

    private static void awardActionSkills(CombatEvent event, Map<String, QuestCombatant> combatants) {
        QuestCombatant actor = combatants.get(event.actorId());
        if (actor == null) {
            throw new IllegalArgumentException("Combat event has an unknown actor.");
        }
        Hero hero = actor.getHero();
        if (hero == null) {
            return;
        }

        if (event.action() == CombatAction.BASIC_ATTACK && attacksCreature(event, combatants)) {
            if (hero.getHeroClass() == HeroClass.WARRIOR) {
                award(hero, HeroSkill.MELEE, BigDecimal.ONE);
            } else if (hero.getHeroClass() == HeroClass.ARCHER) {
                award(hero, HeroSkill.DISTANCE, BigDecimal.ONE);
            }
        }
        if (event.manaSpent() > 0) {
            award(hero, HeroSkill.MAGIC, BigDecimal.valueOf(event.manaSpent()).multiply(MAGIC_POINTS_PER_MANA));
        }
    }

    private static boolean attacksCreature(CombatEvent event, Map<String, QuestCombatant> combatants) {
        return event.hits().stream().anyMatch(hit -> {
            QuestCombatant target = combatants.get(hit.targetId());
            return target != null && target.getTeam() == CombatTeam.CREATURES;
        });
    }

    private static void award(Hero hero, HeroSkill skill, BigDecimal basePoints) {
        hero.addSkillPoints(skill, HeroProgression.combatSkillAward(
                hero.getHeroClass(), skill, basePoints, hero.getStaminaMilliseconds()));
    }
}
