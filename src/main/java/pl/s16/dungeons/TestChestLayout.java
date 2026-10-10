package pl.s16.dungeons;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Pure plan: chest positions are floor+1, within existing ROOM air cells.
 * Do not place chest in room zero (entrance shaft) or adjacent to another chest.
 */
public final class TestChestLayout {
    private TestChestLayout() {}
    public record Spot(int x, int y, int z) {}
    public static List<Spot> plan(DungeonLayoutPlanner.Layout layout, DungeonBlueprint blueprint, long seed) {
        Random r = new Random(seed ^ 0x1c7d4a30L);
        List<Spot> result = new ArrayList<>();
        Set<Spot> used = new HashSet<>();
        for (DungeonLayoutPlanner.Room room : layout.rooms()) {
            if (layout.entrances().contains(room.index()) || r.nextDouble() >= 0.70) continue;
            int count = 1 + r.nextInt(2);
            int inset = room.radius() - 2;
            Spot[] corners = {
                new Spot(room.x()-inset,1,room.z()-inset),
                new Spot(room.x()+inset,1,room.z()+inset),
                new Spot(room.x()-inset,1,room.z()+inset),
                new Spot(room.x()+inset,1,room.z()-inset)
            };
            int start = r.nextInt(corners.length);
            for (int i = 0; i < corners.length && count > 0; i++) {
                Spot spot = corners[(start+i)%corners.length];
                if (blueprint.cells().get(new DungeonBlueprint.Pos(spot.x(),spot.y(),spot.z())) != DungeonBlueprint.Cell.AIR)
                    continue;
                if (used.stream().anyMatch(other -> Math.abs(other.x()-spot.x())<=1 && Math.abs(other.z()-spot.z())<=1))
                    continue;
                used.add(spot);result.add(spot);count--;
            }
        }
        return List.copyOf(result);
    }
}
