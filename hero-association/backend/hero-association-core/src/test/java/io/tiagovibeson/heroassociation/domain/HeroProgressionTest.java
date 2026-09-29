package io.tiagovibeson.heroassociation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class HeroProgressionTest {

    @Test
    void shouldMatchTheDocumentedExperienceThresholds() {
        assertEquals(0, HeroProgression.experienceRequiredForLevel(1));
        assertEquals(100, HeroProgression.experienceRequiredForLevel(2));
        assertEquals(200, HeroProgression.experienceRequiredForLevel(3));
        assertEquals(9_300, HeroProgression.experienceRequiredForLevel(10));
        assertEquals(1_847_300, HeroProgression.experienceRequiredForLevel(50));
        assertEquals(15_694_800, HeroProgression.experienceRequiredForLevel(100));
        assertEquals(1, HeroProgression.levelForExperience(99));
        assertEquals(2, HeroProgression.levelForExperience(100));
        assertEquals(3, HeroProgression.levelForExperience(200));
        assertEquals(100, HeroProgression.levelForExperience(15_694_800));
    }

    @Test
    void shouldCalculateSkillLevelsFromFractionalCumulativePoints() {
        assertEquals(BigInteger.valueOf(100), HeroProgression.pointsForNextSkillLevel(1));
        assertEquals(BigInteger.valueOf(115), HeroProgression.pointsForNextSkillLevel(2));
        assertEquals(BigInteger.valueOf(133), HeroProgression.pointsForNextSkillLevel(3));
        assertEquals(BigInteger.valueOf(352), HeroProgression.pointsForNextSkillLevel(10));
        assertEquals(1, HeroProgression.skillLevelForPoints(BigDecimal.ZERO));
        assertEquals(1, HeroProgression.skillLevelForPoints(new BigDecimal("99.999999")));
        assertEquals(2, HeroProgression.skillLevelForPoints(new BigDecimal("100")));
        assertEquals(3, HeroProgression.skillLevelForPoints(new BigDecimal("215")));
        assertEquals(10, HeroProgression.skillLevelForPoints(new BigDecimal("1683")));
        assertEquals(15, HeroProgression.skillLevelForPoints(new BigDecimal("4058")));
        assertEquals(20, HeroProgression.skillLevelForPoints(new BigDecimal("8831")));
        assertThrows(IllegalArgumentException.class, () -> HeroProgression.pointsForNextSkillLevel(0));
    }

    @Test
    void shouldDeriveResourcesFromLevelWithoutRefillingCurrentResources() {
        Hero warrior = Hero.createRecruitable("Alden", "Steelward", HeroClass.WARRIOR);
        Hero mage = Hero.createRecruitable("Seris", "Dawnflame", HeroClass.MAGE);
        Hero archer = Hero.createRecruitable("Tarin", "Windmark", HeroClass.ARCHER);

        warrior.addExperience(100);
        mage.addExperience(100);
        archer.addExperience(100);

        assertEquals(340, warrior.getMaxHealth());
        assertEquals(60, warrior.getMaxMana());
        assertEquals(115, mage.getMaxHealth());
        assertEquals(560, mage.getMaxMana());
        assertEquals(225, archer.getMaxHealth());
        assertEquals(225, archer.getMaxMana());
        assertEquals(300, warrior.getCurrentHealth());
        assertEquals(50, warrior.getCurrentMana());
    }

    @Test
    void shouldCalculateCreatureXpForEachHeroAtExactStaminaBoundaries() {
        long low = HeroProgression.LOW_STAMINA_MILLISECONDS;
        long high = HeroProgression.HIGH_STAMINA_MILLISECONDS;
        assertEquals(150, HeroProgression.creatureExperienceAward(100, high + 1));
        assertEquals(100, HeroProgression.creatureExperienceAward(100, high));
        assertEquals(100, HeroProgression.creatureExperienceAward(100, low));
        assertEquals(50, HeroProgression.creatureExperienceAward(100, low - 1));
        assertEquals(151, HeroProgression.creatureExperienceAward(101, high + 1));
        assertEquals(50, HeroProgression.creatureExperienceAward(101, low - 1));
    }

    @Test
    void shouldApplyClassAptitudeAndLowStaminaOnlyBelowFifteenHours() {
        long threshold = HeroProgression.LOW_STAMINA_MILLISECONDS;
        assertEquals(0, HeroProgression.combatSkillAward(
                HeroClass.WARRIOR, HeroSkill.MELEE, BigDecimal.ONE, threshold).compareTo(BigDecimal.ONE));
        assertEquals(0, HeroProgression.combatSkillAward(
                HeroClass.ARCHER, HeroSkill.MELEE, BigDecimal.ONE, threshold)
                .compareTo(new BigDecimal("0.25")));
        assertEquals(0, HeroProgression.combatSkillAward(
                HeroClass.MAGE, HeroSkill.MELEE, BigDecimal.ONE, threshold)
                .compareTo(new BigDecimal("0.05")));
        assertEquals(0, HeroProgression.combatSkillAward(
                HeroClass.MAGE, HeroSkill.MAGIC, BigDecimal.ONE, threshold - 1)
                .compareTo(new BigDecimal("0.5")));
        assertEquals(0, HeroProgression.combatSkillAward(
                HeroClass.WARRIOR, HeroSkill.MAGIC, BigDecimal.ONE, threshold - 1)
                .compareTo(new BigDecimal("0.025")));
        assertThrows(IllegalArgumentException.class, () -> HeroProgression.combatSkillAward(
                HeroClass.WARRIOR, HeroSkill.SHIELD, BigDecimal.ONE, threshold));
    }

    @Test
    void shouldKeepPreciseStaminaAndFractionalSkillPoints() {
        Hero hero = Hero.createRecruitable("Alden", "Steelward", HeroClass.WARRIOR);
        hero.consumeStaminaMilliseconds(1);
        assertEquals(HeroProgression.MAX_STAMINA_MILLISECONDS - 1, hero.getStaminaMilliseconds());
        assertEquals(99, hero.getStamina());
        hero.consumeStaminaMilliseconds(Long.MAX_VALUE);
        assertEquals(0, hero.getStaminaMilliseconds());
        hero.recoverStaminaMilliseconds(Long.MAX_VALUE);
        assertEquals(HeroProgression.MAX_STAMINA_MILLISECONDS, hero.getStaminaMilliseconds());

        hero.addSkillPoints(HeroSkill.MAGIC, new BigDecimal("0.05"));
        assertEquals(0, hero.getSkillPoints(HeroSkill.MAGIC).compareTo(new BigDecimal("0.05")));
        assertEquals(1, hero.getMagicLevel());
        assertThrows(IllegalArgumentException.class, () -> hero.addExperience(-1));
        assertThrows(IllegalArgumentException.class, () -> hero.consumeStaminaMilliseconds(-1));
    }
}
