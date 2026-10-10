package pl.s16.dungeons;

/** A very distant anchor must never wrap around the integer border check. */
public final class PlacementOverflowSelfTest {
    public static void main(String[] args) {
        var bounds = new DungeonBlueprint.Bounds(-20, 30, 0, 6, -15, 25);
        int[] extremes = {Integer.MAX_VALUE, Integer.MIN_VALUE, 2_000_000_000, -2_000_000_000};
        for (int p : extremes) {
            if (PlacementRules.validate(p, 0, 0, 0, bounds, 3500, 4000, 50).allowed())
                throw new AssertionError("X integer-overflow accepted: " + p);
            if (PlacementRules.validate(0, p, 0, 0, bounds, 3500, 4000, 50).allowed())
                throw new AssertionError("Z integer-overflow accepted: " + p);
        }
        if (!PlacementRules.validate(0, 0, 0, 0, bounds, 3500, 4000, 50).allowed())
            throw new AssertionError("Ordinary interior site rejected");
        if (PlacementRules.validate(0, 0, 3501, 0, bounds, 3500, 4000, 50).allowed())
            throw new AssertionError("Entrance outside radial border accepted");
        System.out.println("OK: 64-bit border arithmetic rejects extreme world coordinates");
    }
}
