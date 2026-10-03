package io.tiagovibeson.heroassociation.expedition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import io.tiagovibeson.heroassociation.contract.WorldContract.*;
import io.tiagovibeson.heroassociation.domain.UuidV7;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LootResolverTest {
    private static final UUID IRON = UUID.fromString("019c4c00-0070-7000-8000-000000000002");
    private static final UUID CRYSTAL = UUID.fromString("019c4c00-0070-7000-8000-000000000001");

    @Test void allDropsCanCoexistAtBothInclusiveQuantityEndpoints() {
        var creature = creature();
        var minimum = LootResolver.roll(creature, BigDecimal.ONE, rolls(false, 0));
        var maximum = LootResolver.roll(creature, BigDecimal.ONE, rolls(true, 0));
        assertEquals(1, minimum.gold()); assertEquals(25, maximum.gold());
        assertEquals(1, minimum.items().get(IRON)); assertEquals(5, maximum.items().get(IRON));
        assertEquals(1, minimum.items().get(CRYSTAL)); assertEquals(5, maximum.items().get(CRYSTAL));
        assertEquals(7, minimum.runes().size()); assertEquals(minimum.runes(), maximum.runes());
        assertTrue(minimum.runes().values().stream().allMatch(quantity -> quantity == 1));
    }

    @Test void failedGoldDoesNotSkipAnyRuneOrMaterialRoll() {
        var loot = LootResolver.roll(creature(), BigDecimal.ONE, rolls(false, .5, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertEquals(0, loot.gold()); assertEquals(7, loot.runes().size()); assertEquals(2, loot.items().size());
    }

    @Test void aRuneHitNeitherForcesNorSuppressesTheFollowingRuneOrMaterial() {
        // Gold misses, then alternating rune hits/misses; iron misses and crystal hits.
        var loot = LootResolver.roll(creature(), BigDecimal.ONE, rolls(false, .5, 0, .01, 0, .01, 0, .01, 0, .05, 0));
        assertEquals(4, loot.runes().size());
        assertFalse(loot.runes().containsKey(rune(2))); assertTrue(loot.runes().containsKey(rune(3)));
        assertEquals(java.util.Map.of(CRYSTAL, 1), loot.items());
    }

    @Test void chanceThresholdsAreExclusiveAndRatesChangeChanceRatherThanQuantity() {
        var noDrops = LootResolver.roll(creature(), BigDecimal.ONE, rolls(false, .5, .01, .01, .01, .01, .01, .01, .01, .05, .05));
        assertEquals(RunState.CarriedAssets.empty(), noDrops);
        var doubled = LootResolver.roll(creature(), new BigDecimal("2"), rolls(true, .99, .019, .019, .019, .019, .019, .019, .019, .099, .099));
        assertEquals(25, doubled.gold()); assertEquals(7, doubled.runes().size());
        assertEquals(5, doubled.items().get(IRON)); assertEquals(5, doubled.items().get(CRYSTAL));
        assertEquals(RunState.CarriedAssets.empty(), LootResolver.roll(creature(), BigDecimal.ZERO, rolls(false, 0)));
    }

    @Test void fixedSeedSamplingExercisesTheSpecifiedRatesRangesAndMultipleRuneHits() {
        var creature = creature(); var random = new Random(7331);
        int goldHits = 0, ironHits = 0, crystalHits = 0, multipleRunes = 0;
        int[] runeHits = new int[7]; boolean[] goldAmounts = new boolean[26], ironAmounts = new boolean[6], crystalAmounts = new boolean[6];
        for (int index = 0; index < 100_000; index++) {
            var loot = LootResolver.roll(creature, BigDecimal.ONE, random);
            if (loot.gold() > 0) { goldHits++; assertTrue(loot.gold() <= 25); goldAmounts[(int) loot.gold()] = true; }
            if (loot.items().containsKey(IRON)) { ironHits++; ironAmounts[loot.items().get(IRON)] = true; }
            if (loot.items().containsKey(CRYSTAL)) { crystalHits++; crystalAmounts[loot.items().get(CRYSTAL)] = true; }
            if (loot.runes().size() > 1) multipleRunes++;
            for (int number = 1; number <= 7; number++) if (loot.runes().containsKey(rune(number))) runeHits[number - 1]++;
        }
        assertTrue(goldHits > 49_000 && goldHits < 51_000);
        assertTrue(ironHits > 4_700 && ironHits < 5_300); assertTrue(crystalHits > 4_700 && crystalHits < 5_300);
        for (int hits : runeHits) assertTrue(hits > 850 && hits < 1150);
        assertTrue(multipleRunes > 100);
        for (int quantity = 1; quantity <= 25; quantity++) assertTrue(goldAmounts[quantity]);
        for (int quantity = 1; quantity <= 5; quantity++) { assertTrue(ironAmounts[quantity]); assertTrue(crystalAmounts[quantity]); }
    }

    @Test void invalidRangesAndChancesAreRejectedAndTheLargestIntegerQuantityIsSafe() {
        assertThrows(IllegalArgumentException.class, () -> new GoldDrop(0, 25, .5));
        assertThrows(IllegalArgumentException.class, () -> new GoldDrop(25, 1, .5));
        assertThrows(IllegalArgumentException.class, () -> new GoldDrop(1, 25, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Drop("ITEM", IRON, 5, 1, .05));
        assertThrows(IllegalArgumentException.class, () -> new Drop("RUNE", rune(1), 1, 1, 1.01));
        var largest = new Creature(UuidV7.next(), 1, "Fixture", 100, 1, 0, 0, 1600, 0, 0, 0, 2,
                new GoldDrop(Integer.MAX_VALUE, Integer.MAX_VALUE, 1), List.of());
        assertEquals(Integer.MAX_VALUE, LootResolver.roll(largest, BigDecimal.ONE, new Random(0)).gold());
    }

    private static UUID rune(int number) { return UUID.fromString("019c4c00-0020-7000-8000-%012d".formatted(number)); }
    private static Creature creature() {
        List<Drop> drops = new ArrayList<>();
        for (int number = 1; number <= 7; number++) drops.add(new Drop("RUNE", rune(number), 1, 1, .01));
        drops.add(new Drop("ITEM", IRON, 1, 5, .05)); drops.add(new Drop("ITEM", CRYSTAL, 1, 5, .05));
        return new Creature(UuidV7.next(), 1, "Fixture", 100, 1, 0, 0, 1600, 0, 0, 0, 2, new GoldDrop(1, 25, .5), drops);
    }

    private static Random rolls(boolean maximum, double... chances) {
        return new Random(0) {
            private int index;
            @Override public double nextDouble() { return chances.length == 1 ? chances[0] : chances[index++]; }
            @Override public long nextLong(long origin, long bound) { return maximum ? bound - 1 : origin; }
        };
    }
}
