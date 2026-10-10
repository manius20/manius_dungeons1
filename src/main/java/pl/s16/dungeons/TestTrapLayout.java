package pl.s16.dungeons;

import java.util.*;

/** Plans optional test-world traps without blocking the central room routes.
 * The geometric plan never changes Minecraft world state by itself.
 */
public final class TestTrapLayout {
    private TestTrapLayout() {}
    public enum Kind { COBWEB, MAGMA }
    public record Spot(int room, int x, int y, int z, Kind kind) {}

    public static List<Spot> plan(DungeonLayoutPlanner.Layout layout, DungeonBlueprint blueprint,
                                  long seed, double fraction) {
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0)
            throw new IllegalArgumentException("Trap fraction must be 0..1");
        Random random = new Random(seed ^ 0x3a7d12faL);
        List<Spot> spots = new ArrayList<>();
        List<TestChestLayout.Spot> chests = TestChestLayout.plan(layout, blueprint, seed);
        // A trap is off centre; the centre remains walkable and never closes a key room.
        for (DungeonLayoutPlanner.Room room : layout.rooms()) {
            if (layout.entrances().contains(room.index()) || random.nextDouble() >= fraction) continue;
            Kind kind = random.nextBoolean() ? Kind.COBWEB : Kind.MAGMA;
            int x = room.x() + (random.nextBoolean() ? 2 : -2);
            int z = room.z() + (random.nextBoolean() ? 2 : -2);
            int y = kind == Kind.COBWEB ? 1 : 0;
            DungeonBlueprint.Cell required = kind == Kind.COBWEB ? DungeonBlueprint.Cell.AIR : DungeonBlueprint.Cell.FLOOR;
            if (blueprint.cells().get(new DungeonBlueprint.Pos(x,y,z)) != required) continue;
            // Keep a two-block gap from every generated chest, including diagonal adjacency.
            boolean nearChest = chests.stream().anyMatch(c ->
                Math.abs(c.x()-x) <= 1 && Math.abs(c.z()-z) <= 1);
            if (!nearChest) spots.add(new Spot(room.index(), x, y, z, kind));
        }
        return List.copyOf(spots);
    }
}
