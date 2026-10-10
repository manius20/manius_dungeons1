package pl.s16.dungeons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Pure Java loot probability engine: each pool slot gets its own chance roll.
 * Empty pools or unsuccessful rolls produce no item, never an invented reward.
 * A per-chest weak/good pool roll is independent of per-item slot chances.
 */
public final class LootPicker {
    private LootPicker() {}
    public record Entry<T>(T item, double percent) {
        public Entry {
            Objects.requireNonNull(item, "item");
            if (!Double.isFinite(percent) || percent < 0 || percent > 100)
                throw new IllegalArgumentException("Chance must be in [0,100]");
        }
    }
    public static <T> List<T> roll(List<Entry<T>> entries, Random random, int maxItems) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(random, "random");
        if (maxItems < 0 || maxItems > 54) throw new IllegalArgumentException("Invalid maxItems");
        List<T> results = new ArrayList<>();
        for (Entry<T> entry : entries) {
            if (random.nextDouble() * 100.0 < entry.percent()) results.add(entry.item());
        }
        // Randomized truncation avoids favoring low-numbered GUI slots.
        Collections.shuffle(results, random);
        return List.copyOf(results.subList(0, Math.min(maxItems, results.size())));
    }
    public static boolean chooseWeak(Random random, double weakProbability) {
        if (!Double.isFinite(weakProbability) || weakProbability < 0 || weakProbability > 1)
            throw new IllegalArgumentException("Weak probability must be in [0,1]");
        return random.nextDouble() < weakProbability;
    }
}
