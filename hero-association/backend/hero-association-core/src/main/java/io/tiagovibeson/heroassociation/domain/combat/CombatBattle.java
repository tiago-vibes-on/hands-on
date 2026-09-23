package io.tiagovibeson.heroassociation.domain.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CombatBattle {

    private static final long RECOVERY_INTERVAL_MILLISECONDS = 1_000;
    private static final long SPELL_RETRY_INTERVAL_MILLISECONDS = 1_000;

    private final List<Combatant> heroes;
    private final List<Combatant> creatures;
    private long currentTimeMilliseconds;
    private long nextRecoveryAt = RECOVERY_INTERVAL_MILLISECONDS;
    private CombatStatus status = CombatStatus.IN_PROGRESS;

    private CombatBattle(List<Combatant> heroes, List<Combatant> creatures) {
        this.heroes = List.copyOf(heroes);
        this.creatures = List.copyOf(creatures);
        validateTeams();
        updateStatus();
        if (status == CombatStatus.IN_PROGRESS) {
            scheduleOpeningActions();
        }
    }

    public static CombatBattle start(List<Combatant> heroes, List<Combatant> creatures) {
        return new CombatBattle(heroes, creatures);
    }

    public static CombatBattle restore(CombatBattleSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        CombatBattle battle = new CombatBattle(
                snapshot.heroes().stream().map(Combatant::restore).toList(),
                snapshot.creatures().stream().map(Combatant::restore).toList(),
                snapshot.currentTimeMilliseconds(),
                snapshot.nextRecoveryAt(),
                snapshot.status());
        return battle;
    }

    private CombatBattle(
            List<Combatant> heroes,
            List<Combatant> creatures,
            long currentTimeMilliseconds,
            long nextRecoveryAt,
            CombatStatus status) {
        this.heroes = List.copyOf(heroes);
        this.creatures = List.copyOf(creatures);
        validateTeams();
        this.currentTimeMilliseconds = currentTimeMilliseconds;
        this.nextRecoveryAt = nextRecoveryAt;
        this.status = status;
    }

    public long getCurrentTimeMilliseconds() {
        return currentTimeMilliseconds;
    }

    public CombatStatus getStatus() {
        return status;
    }

    public List<Combatant> getHeroes() {
        return heroes;
    }

    public List<Combatant> getCreatures() {
        return creatures;
    }

    public CombatBattleSnapshot snapshot() {
        return new CombatBattleSnapshot(
                currentTimeMilliseconds,
                nextRecoveryAt,
                status,
                heroes.stream().map(Combatant::snapshot).toList(),
                creatures.stream().map(Combatant::snapshot).toList());
    }

    public List<CombatEvent> advanceTo(long targetTimeMilliseconds, CombatRandom random) {
        if (targetTimeMilliseconds < currentTimeMilliseconds) {
            throw new IllegalArgumentException("target time must not move backwards");
        }
        Objects.requireNonNull(random, "random must not be null");

        List<CombatEvent> events = new ArrayList<>();
        while (status == CombatStatus.IN_PROGRESS) {
            long nextActionAt = nextActionAt();
            if (nextActionAt > targetTimeMilliseconds) {
                break;
            }

            currentTimeMilliseconds = nextActionAt;
            recoverHeroes(events);
            resolveSpells(events, random);
            resolveBasicAttacks(heroes, events, random);
            resolveBasicAttacks(creatures, events, random);
        }

        currentTimeMilliseconds = targetTimeMilliseconds;
        return List.copyOf(events);
    }

    private void scheduleOpeningActions() {
        for (int index = 0; index < heroes.size(); index++) {
            Combatant hero = heroes.get(index);
            hero.scheduleBasicAttackAt(480 + (index * 170L));
            scheduleOpeningSpells(hero);
        }
        for (int index = 0; index < creatures.size(); index++) {
            creatures.get(index).scheduleBasicAttackAt(760 + (index * 160L));
        }
    }

    private void scheduleOpeningSpells(Combatant hero) {
        int spellIndex = 0;
        for (CombatSpell spell : CombatSpell.values()) {
            if (hero.knows(spell)) {
                hero.scheduleSpellCastAt(spell, 900 + (spellIndex * 450L));
                spellIndex++;
            }
        }
    }

    private long nextActionAt() {
        long next = nextRecoveryAt;
        for (Combatant combatant : allCombatants()) {
            if (!combatant.isAlive()) {
                continue;
            }

            next = Math.min(next, combatant.getNextBasicAttackAt());
            for (CombatSpell spell : CombatSpell.values()) {
                if (combatant.knows(spell)) {
                    next = Math.min(next, combatant.getNextSpellCastAt(spell));
                }
            }
        }
        return next;
    }

    private void recoverHeroes(List<CombatEvent> events) {
        if (currentTimeMilliseconds != nextRecoveryAt) {
            return;
        }

        nextRecoveryAt += RECOVERY_INTERVAL_MILLISECONDS;
        for (Combatant hero : heroes) {
            if (!hero.isAlive()) {
                continue;
            }
            int healthRecovered = hero.recoverHealth();
            int manaRecovered = hero.recoverMana();
            if (healthRecovered > 0 || manaRecovered > 0) {
                events.add(new CombatEvent(
                        currentTimeMilliseconds,
                        CombatAction.RECOVERY,
                        hero.getId(),
                        List.of(),
                        0,
                        healthRecovered,
                        manaRecovered));
            }
        }
    }

    private void resolveSpells(List<CombatEvent> events, CombatRandom random) {
        for (Combatant hero : heroes) {
            if (!hero.isAlive() || status != CombatStatus.IN_PROGRESS) {
                return;
            }

            for (CombatSpell spell : CombatSpell.values()) {
                if (hero.knows(spell) && hero.getNextSpellCastAt(spell) == currentTimeMilliseconds) {
                    resolveSpell(hero, spell, events, random);
                    if (status != CombatStatus.IN_PROGRESS) {
                        return;
                    }
                }
            }
        }
    }

    private void resolveSpell(Combatant caster, CombatSpell spell, List<CombatEvent> events, CombatRandom random) {
        if (!caster.hasManaFor(spell)) {
            caster.scheduleSpellCastAt(spell, currentTimeMilliseconds + SPELL_RETRY_INTERVAL_MILLISECONDS);
            return;
        }

        List<Combatant> targets = livingCombatants(creatures);
        if (targets.isEmpty()) {
            updateStatus();
            return;
        }
        if (!spell.affectsAllOpponents()) {
            targets = List.of(targets.getFirst());
        }

        caster.spendMana(spell.getManaCost());
        int baseDamage = spell.damageForMagicLevel(caster.getMagicLevel());
        List<CombatHit> hits = new ArrayList<>();
        for (Combatant target : targets) {
            hits.add(resolveHit(caster, target, baseDamage, random));
        }
        caster.scheduleSpellCastAt(spell, currentTimeMilliseconds + spell.getCooldownMilliseconds());
        events.add(new CombatEvent(
                currentTimeMilliseconds,
                spell.getAction(),
                caster.getId(),
                hits,
                spell.getManaCost(),
                0,
                0));
        updateStatus();
    }

    private void resolveBasicAttacks(List<Combatant> attackers, List<CombatEvent> events, CombatRandom random) {
        for (Combatant attacker : attackers) {
            if (status != CombatStatus.IN_PROGRESS) {
                return;
            }
            if (!attacker.isAlive() || attacker.getNextBasicAttackAt() != currentTimeMilliseconds) {
                continue;
            }

            List<Combatant> targets = attacker.getTeam() == CombatTeam.HEROES ? creatures : heroes;
            Combatant target = livingCombatants(targets).stream().findFirst().orElse(null);
            if (target == null) {
                updateStatus();
                return;
            }

            CombatHit hit = resolveHit(attacker, target, attacker.getAttackDamage(), random);
            attacker.scheduleBasicAttackAt(currentTimeMilliseconds + attacker.getAttackIntervalMilliseconds());
            events.add(new CombatEvent(
                    currentTimeMilliseconds,
                    CombatAction.BASIC_ATTACK,
                    attacker.getId(),
                    List.of(hit),
                    0,
                    0,
                    0));
            updateStatus();
        }
    }

    private CombatHit resolveHit(Combatant attacker, Combatant target, int baseDamage, CombatRandom random) {
        boolean critical = attacker.getCriticalChance() > 0 && random.nextDouble() < attacker.getCriticalChance();
        int damage = critical
                ? (int) Math.round(baseDamage * attacker.getCriticalDamageMultiplier())
                : baseDamage;
        boolean defeated = target.takeDamage(damage);
        return new CombatHit(target.getId(), damage, critical, defeated);
    }

    private void updateStatus() {
        if (livingCombatants(creatures).isEmpty()) {
            status = CombatStatus.HERO_VICTORY;
        } else if (livingCombatants(heroes).isEmpty()) {
            status = CombatStatus.CREATURE_VICTORY;
        }
    }

    private List<Combatant> allCombatants() {
        List<Combatant> allCombatants = new ArrayList<>(heroes.size() + creatures.size());
        allCombatants.addAll(heroes);
        allCombatants.addAll(creatures);
        return allCombatants;
    }

    private static List<Combatant> livingCombatants(List<Combatant> combatants) {
        return combatants.stream().filter(Combatant::isAlive).toList();
    }

    private void validateTeams() {
        if (heroes.isEmpty() || creatures.isEmpty()) {
            throw new IllegalArgumentException("a battle needs at least one hero and one creature");
        }
        if (heroes.stream().anyMatch(hero -> hero.getTeam() != CombatTeam.HEROES)) {
            throw new IllegalArgumentException("heroes must belong to the hero team");
        }
        if (creatures.stream().anyMatch(creature -> creature.getTeam() != CombatTeam.CREATURES)) {
            throw new IllegalArgumentException("creatures must belong to the creature team");
        }
    }
}
