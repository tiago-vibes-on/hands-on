package io.tiagovibeson.heroassociation.combat.application;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.tiagovibeson.heroassociation.combat.api.CreatureBattleInput;
import io.tiagovibeson.heroassociation.combat.api.HeroBattleClass;
import io.tiagovibeson.heroassociation.combat.api.HeroBattleInput;
import io.tiagovibeson.heroassociation.combat.api.RuneBattleEffect;
import io.tiagovibeson.heroassociation.combat.api.RuneBattleInput;
import io.tiagovibeson.heroassociation.combat.api.StartBattleRequest;
import io.tiagovibeson.heroassociation.domain.combat.CombatBattleSnapshot;
import io.tiagovibeson.heroassociation.domain.combat.CombatSpell;
import io.tiagovibeson.heroassociation.domain.combat.CombatantSnapshot;
import jakarta.ws.rs.BadRequestException;

final class BattleInputValidator {

    private static final long MAX_STAMINA_MILLISECONDS = Duration.ofHours(48).toMillis();
    private static final int RUNE_SLOT_COUNT = 5;

    private BattleInputValidator() {
    }

    static void validate(StartBattleRequest request, CombatBattleSnapshot snapshot) {
        if (request.heroInputs() == null
                || request.creatureInputs() == null
                || request.heroInputs().size() != snapshot.heroes().size()
                || request.creatureInputs().size() != snapshot.creatures().size()) {
            throw new BadRequestException("Pinned inputs must match the opening formation.");
        }

        Set<UUID> heroIds = new HashSet<>();
        for (int index = 0; index < request.heroInputs().size(); index++) {
            validateHero(request.heroInputs().get(index), snapshot.heroes().get(index), heroIds);
        }
        for (int index = 0; index < request.creatureInputs().size(); index++) {
            validateCreature(request.creatureInputs().get(index), snapshot.creatures().get(index));
        }
    }

    private static void validateHero(HeroBattleInput input, CombatantSnapshot combatant, Set<UUID> heroIds) {
        if (input == null
                || !matches(input.combatantId(), combatant)
                || !isUuidV7(input.heroId())
                || !heroIds.add(input.heroId())
                || input.heroClass() == null
                || input.level() < 1
                || input.meleeLevel() < 1
                || input.distanceLevel() < 1
                || input.shieldLevel() < 1
                || input.magicLevel() < 1
                || input.magicLevel() != combatant.magicLevel()
                || input.startingStaminaMilliseconds() < 0
                || input.startingStaminaMilliseconds() > MAX_STAMINA_MILLISECONDS
                || input.runeSlots() == null) {
            throw new BadRequestException("Invalid pinned Hero identity or progression inputs.");
        }

        List<CombatSpell> expectedSpells = input.heroClass() == HeroBattleClass.MAGE
                ? List.of(CombatSpell.values()) : List.of();
        if (!combatant.spells().equals(expectedSpells)) {
            throw new BadRequestException("Hero class and opening spells do not match.");
        }

        Set<Integer> occupiedSlots = new HashSet<>();
        double criticalChance = 0;
        double criticalDamageBonus = 0;
        for (RuneBattleInput rune : input.runeSlots()) {
            if (rune == null
                    || !isUuidV7(rune.runeId())
                    || rune.slotIndex() < 0
                    || rune.slotIndex() >= RUNE_SLOT_COUNT
                    || !occupiedSlots.add(rune.slotIndex())
                    || rune.code() == null
                    || rune.code().isBlank()
                    || rune.effect() == null
                    || !Double.isFinite(rune.effectValue())
                    || rune.effectValue() < 0) {
                throw new BadRequestException("Invalid pinned rune slot.");
            }
            if (rune.effect() == RuneBattleEffect.CRITICAL_CHANCE) {
                criticalChance += rune.effectValue();
            } else if (rune.effect() == RuneBattleEffect.CRITICAL_DAMAGE) {
                criticalDamageBonus += rune.effectValue();
            }
        }
        if (!Double.isFinite(criticalChance)
                || !Double.isFinite(criticalDamageBonus)
                || Math.abs(combatant.criticalChance() - Math.min(1, criticalChance)) > 1e-9
                || Math.abs(combatant.criticalDamageMultiplier() - (2 + criticalDamageBonus)) > 1e-9) {
            throw new BadRequestException("Pinned runes do not match effective critical stats.");
        }
    }

    private static void validateCreature(CreatureBattleInput input, CombatantSnapshot combatant) {
        if (input == null
                || !matches(input.combatantId(), combatant)
                || !isUuidV7(input.definitionId())
                || input.definitionVersion() < 1
                || input.baseExperience() < 0
                || combatant.magicLevel() != 0
                || !combatant.spells().isEmpty()) {
            throw new BadRequestException("Invalid pinned Creature definition or experience.");
        }
    }

    private static boolean matches(UUID combatantId, CombatantSnapshot combatant) {
        return isUuidV7(combatantId) && combatantId.toString().equals(combatant.id());
    }

    private static boolean isUuidV7(UUID value) {
        return value != null && value.version() == 7;
    }
}
