package io.tiagovibeson.heroassociation.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Objects;

public final class HeroProgression {

    public static final int STARTING_SKILL_LEVEL = 1;
    public static final long MAX_STAMINA_MILLISECONDS = Duration.ofHours(48).toMillis();
    public static final long LOW_STAMINA_MILLISECONDS = Duration.ofHours(15).toMillis();
    public static final long HIGH_STAMINA_MILLISECONDS = Duration.ofHours(40).toMillis();

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigInteger THREE = BigInteger.valueOf(3);
    private static final BigInteger TWENTY = BigInteger.valueOf(20);
    private static final BigInteger TWENTY_THREE = BigInteger.valueOf(23);

    private HeroProgression() {
    }

    public static long experienceRequiredForLevel(int level) {
        return experienceThreshold(level).longValueExact();
    }

    public static int levelForExperience(long experience) {
        if (experience < 0) {
            throw new IllegalArgumentException("Experience cannot be negative.");
        }

        BigInteger total = BigInteger.valueOf(experience);
        int upper = 2;
        while (experienceThreshold(upper).compareTo(total) <= 0) {
            upper = Math.multiplyExact(upper, 2);
        }

        int lower = upper / 2;
        while (lower + 1 < upper) {
            int middle = lower + (upper - lower) / 2;
            if (experienceThreshold(middle).compareTo(total) <= 0) {
                lower = middle;
            } else {
                upper = middle;
            }
        }
        return lower;
    }

    public static long creatureExperienceAward(int baseExperience, long staminaMilliseconds) {
        if (baseExperience < 0) {
            throw new IllegalArgumentException("Creature base experience cannot be negative.");
        }
        if (staminaMilliseconds < 0 || staminaMilliseconds > MAX_STAMINA_MILLISECONDS) {
            throw new IllegalArgumentException("Stamina must be within zero and 48 hours.");
        }
        if (staminaMilliseconds > HIGH_STAMINA_MILLISECONDS) {
            return Math.multiplyExact((long) baseExperience, 3) / 2;
        }
        return staminaMilliseconds < LOW_STAMINA_MILLISECONDS
                ? baseExperience / 2L : baseExperience;
    }

    public static BigDecimal combatSkillAward(
            HeroClass heroClass, HeroSkill skill, BigDecimal basePoints, long staminaMilliseconds) {
        Objects.requireNonNull(heroClass);
        Objects.requireNonNull(skill);
        if (basePoints == null || basePoints.signum() < 0) {
            throw new IllegalArgumentException("Base skill points cannot be null or negative.");
        }
        if (staminaMilliseconds < 0 || staminaMilliseconds > MAX_STAMINA_MILLISECONDS) {
            throw new IllegalArgumentException("Stamina must be within zero and 48 hours.");
        }

        BigDecimal award = basePoints.multiply(heroClass.getSkillAptitude(skill));
        return staminaMilliseconds < LOW_STAMINA_MILLISECONDS ? award.multiply(HALF) : award;
    }

    public static BigInteger pointsForNextSkillLevel(int currentLevel) {
        if (currentLevel < STARTING_SKILL_LEVEL) {
            throw new IllegalArgumentException("Skill level cannot be below the starting level.");
        }

        int exponent = currentLevel - STARTING_SKILL_LEVEL;
        BigInteger numerator = BigInteger.valueOf(100).multiply(TWENTY_THREE.pow(exponent));
        BigInteger denominator = TWENTY.pow(exponent);
        BigInteger[] result = numerator.divideAndRemainder(denominator);
        return result[0].add(result[1].signum() == 0 ? BigInteger.ZERO : BigInteger.ONE);
    }

    public static int skillLevelForPoints(BigDecimal points) {
        if (points == null || points.signum() < 0) {
            throw new IllegalArgumentException("Skill points cannot be null or negative.");
        }

        int level = STARTING_SKILL_LEVEL;
        BigDecimal threshold = BigDecimal.ZERO;
        while (true) {
            BigDecimal next = threshold.add(new BigDecimal(pointsForNextSkillLevel(level)));
            if (points.compareTo(next) < 0) {
                return level;
            }
            threshold = next;
            level = Math.incrementExact(level);
        }
    }

    private static BigInteger experienceThreshold(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("Level must be positive.");
        }

        BigInteger value = BigInteger.valueOf(level);
        BigInteger numerator = value.pow(3).multiply(BigInteger.valueOf(50))
                .subtract(value.pow(2).multiply(BigInteger.valueOf(300)))
                .add(value.multiply(BigInteger.valueOf(850)));
        return numerator.divide(THREE).subtract(BigInteger.valueOf(200));
    }
}
