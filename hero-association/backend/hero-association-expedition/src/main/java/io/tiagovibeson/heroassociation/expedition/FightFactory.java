package io.tiagovibeson.heroassociation.expedition;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattle;
import io.tiagovibeson.heroassociation.domain.combat.CombatSpell;
import io.tiagovibeson.heroassociation.domain.combat.CombatTeam;
import io.tiagovibeson.heroassociation.domain.combat.Combatant;
import io.tiagovibeson.heroassociation.expedition.RunState.CreatureProfile;
import io.tiagovibeson.heroassociation.expedition.RunState.FightState;
import io.tiagovibeson.heroassociation.expedition.RunState.HeroState;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FightFactory {

    private final SecureRandom seedSource = new SecureRandom();

    public FightState start(List<HeroState> heroes, CreatureProfile creature, Instant startedAt) {
        List<Combatant> party = new ArrayList<>(heroes.size());
        for (HeroState hero : heroes) {
            HeroClass heroClass = hero.heroClass();
            party.add(new Combatant(
                    hero.heroId().toString(), hero.name(), CombatTeam.HEROES,
                    hero.maxHealth(), hero.maxMana(), hero.health(), hero.mana(),
                    heroClass.getBaseAttackDamage(), heroClass.getBasicAttackManaCost(),
                    heroClass.getAttackIntervalMilliseconds(), heroClass.getHealthRecoveryPerSecond(),
                    heroClass.getManaRecoveryPerSecond(), hero.skillLevel(HeroSkill.MAGIC),
                    hero.criticalChance(), hero.criticalDamageMultiplier(),
                    heroClass == HeroClass.MAGE ? List.of(CombatSpell.values()) : List.of()));
        }
        Combatant troll = new Combatant(
                UuidV7.next().toString(), creature.name(), CombatTeam.CREATURES,
                creature.maxHealth(), creature.maxMana(), creature.maxHealth(), creature.maxMana(),
                creature.attackDamage(), 0, creature.attackIntervalMilliseconds(),
                creature.healthRecoveryPerSecond(), creature.manaRecoveryPerSecond(), 0,
                creature.criticalChance(), creature.criticalDamageMultiplier(), List.of());
        CombatBattle battle = CombatBattle.start(party, List.of(troll));
        return new FightState(UuidV7.next(), startedAt, seedSource.nextLong(),
                "core-v1", "java-random-v1", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                battle.snapshot());
    }
}
