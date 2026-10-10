package pl.s16.dungeons;

import java.util.ArrayList;
import java.util.List;

/**
 * Evenly distribute an underground audit budget over the entire block plan.
 * A strict maximum applies even for large 100-room blueprints. Read-only.
 */
public final class SubsurfaceSampleIndices {
    private SubsurfaceSampleIndices() {}

    public static List<Integer> select(int totalCells, int budget) {
        if (totalCells <= 0 || budget <= 0)
            throw new IllegalArgumentException("Positive cell count and budget required");
        int count = Math.min(totalCells, budget);
        List<Integer> indices = new ArrayList<>(count);
        // Centre of each evenly spaced bucket, avoiding early-cluster bias.
        for (int k = 0; k < count; k++) {
            long numerator = (2L * k + 1L) * totalCells;
            int index = (int) (numerator / (2L * count));
            indices.add(index);
        }
        return List.copyOf(indices);
    }
}
