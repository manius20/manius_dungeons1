package pl.s16.dungeons;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

/**
 * Pure timing and text policy for public notices. A schedule alone does not
 * authorize any message: the caller MUST verify a fully built test instance.
 */
public final class DungeonStartNotices {
    private DungeonStartNotices() {}

    /**
     * Reports just the newest milestone crossed by the once-per-minute ticker.
     * This handles small lag without replaying obsolete announcements after
     * a large pause or after the start of an event.
     */
    public static OptionalInt due(Instant previous, Instant now, Instant start, boolean built) {
        return due(previous, now, start, built, 90);
    }

    /**
     * Lag tolerance in seconds. Never claim "30 minutes" when 10 or 5 remain.
     * At most one (newest) milestone is emitted per tick; the caller owns
     * per-occurrence deduplication and verifies a built test in its own world.
     */
    public static OptionalInt due(Instant previous, Instant now, Instant start,
                                  boolean built, int graceSeconds) {
        if (!built || previous == null || now == null || start == null || !now.isBefore(start)
            || graceSeconds < 0 || graceSeconds > 300)
            return OptionalInt.empty();
        List<Integer> crossed = EventTimeline.crossedMilestones(previous, now, start, List.of(30, 10));
        for (int i=crossed.size()-1; i>=0; i--) {
            int minutes=crossed.get(i);
            Instant milestone=start.minusSeconds(minutes*60L);
            if (!now.isBefore(milestone) && now.getEpochSecond()-milestone.getEpochSecond() <= graceSeconds)
                return OptionalInt.of(minutes);
        }
        return OptionalInt.empty();
    }

    public static String entranceCount(int count) {
        if (count < 1) throw new IllegalArgumentException("Dungeon must have an actual entrance");
        int ones = count % 10, tens = count % 100;
        String noun = count == 1 ? "wejście" :
            (ones >= 2 && ones <= 4 && (tens < 12 || tens > 14)) ? "wejścia" : "wejść";
        return count + " " + noun;
    }

    public static String duration(int minutes) {
        if (minutes < 1) throw new IllegalArgumentException("Duration must be positive");
        int hours=minutes/60, rest=minutes%60;
        if(hours==0)return minutes+" min";
        if(rest==0)return hours+" godz.";
        return hours+" godz. "+rest+" min";
    }

    public static String tenMinuteDetails(int actualEntrances,int durationMinutes) {
        return "Liczba wejść: " + entranceCount(actualEntrances)
            + "; czas trwania: " + duration(durationMinutes) + ".";
    }
}
