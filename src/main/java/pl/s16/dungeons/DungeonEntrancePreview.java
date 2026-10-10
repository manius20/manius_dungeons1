package pl.s16.dungeons;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only entrance geometry preview. Coordinates are LOCAL to the layout,
 * never published as real X/Z. Real surface shafts require a separate terrain
 * survey and snapshot of every proposed entrance before any block mutations.
 */
public final class DungeonEntrancePreview {
    private DungeonEntrancePreview() {}

    public record ProposedEntrance(int roomIndex, int relativeX, int relativeZ) {}

    public static List<ProposedEntrance> forLayout(DungeonLayoutPlanner.Layout layout) {
        if (layout == null || layout.rooms() == null || layout.rooms().isEmpty())
            throw new IllegalArgumentException("Brak pokoi w planie");
        if (layout.entrances() == null || layout.entrances().isEmpty()
            || layout.entrances().size() > 10)
            throw new IllegalArgumentException("Nieprawidlowa liczba wejsc");
        if (layout.entrances().get(0) != 0)
            throw new IllegalArgumentException("Glowne wejscie musi prowadzic do pokoju 0");
        if (!allRoomsReachable(layout))
            throw new IllegalArgumentException("Nie wszystkie pokoje maja polaczenie z wejsciem");
        Set<Integer> seen = new HashSet<>();
        List<ProposedEntrance> result = new ArrayList<>();
        for (int index : layout.entrances()) {
            if (index < 0 || index >= layout.rooms().size() || !seen.add(index))
                throw new IllegalArgumentException("Powtorzone lub niepoprawne pomieszczenie wejsciowe");
            var room = layout.rooms().get(index);
            result.add(new ProposedEntrance(index, room.x(), room.z()));
        }
        return List.copyOf(result);
    }

    private static boolean allRoomsReachable(DungeonLayoutPlanner.Layout layout) {
        Set<Integer> visited = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(0); visited.add(0);
        while (!queue.isEmpty()) {
            int room = queue.removeFirst();
            for (var corridor : layout.corridors()) {
                int target = corridor.from() == room ? corridor.to()
                    : corridor.to() == room ? corridor.from() : -1;
                if (target >= 0 && target < layout.rooms().size() && visited.add(target))
                    queue.add(target);
            }
        }
        return visited.size() == layout.rooms().size();
    }
}
