package io.tiagovibeson.heroassociation.expedition;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.random.RandomGenerator;
import io.tiagovibeson.heroassociation.contract.WorldContract.Creature;

/** One independent chance per entry and defeated Creature; quantities are uniform and inclusive. */
final class LootResolver {
    private LootResolver() { }

    static RunState.CarriedAssets roll(Creature creature, BigDecimal dropRate, RandomGenerator random) {
        var goldDrop = creature.goldDrop();
        long gold = quantity(goldDrop.minimumQuantity(), goldDrop.maximumQuantity(), goldDrop.chance(), dropRate, random);
        Map<UUID, Integer> items = new TreeMap<>();
        Map<UUID, Integer> runes = new TreeMap<>();
        for (var drop : creature.drops()) {
            int amount = quantity(drop.minimumQuantity(), drop.maximumQuantity(), drop.chance(), dropRate, random);
            if (amount > 0) ("ITEM".equals(drop.resourceType()) ? items : runes).merge(drop.resourceId(), amount, Math::addExact);
        }
        return new RunState.CarriedAssets(gold, items, runes);
    }

    private static int quantity(int minimum, int maximum, double chance, BigDecimal rate, RandomGenerator random) {
        double effectiveChance = BigDecimal.valueOf(chance).multiply(rate).min(BigDecimal.ONE).doubleValue();
        if (random.nextDouble() >= effectiveChance) return 0;
        // The long exclusive bound also permits Integer.MAX_VALUE as an inclusive quantity.
        return (int) random.nextLong(minimum, (long) maximum + 1);
    }
}
