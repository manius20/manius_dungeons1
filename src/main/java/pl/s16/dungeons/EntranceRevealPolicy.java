package pl.s16.dungeons;

import java.time.*;

/** Pure logic: a scheduled event by itself is NOT a public dungeon. */
public final class EntranceRevealPolicy {
    private EntranceRevealPolicy() {}
    public static boolean publicVisible(Instant now, Instant start, Instant end, int leadMinutes, boolean actualActiveTest) {
        if (!actualActiveTest || leadMinutes < 0 || leadMinutes > 720 || !end.isAfter(start)) return false;
        return !now.isBefore(start.minus(Duration.ofMinutes(leadMinutes))) && now.isBefore(end);
    }
    public static boolean entranceOpen(Instant now, Instant start, Instant end, boolean actualActiveTest) {
        return actualActiveTest && !now.isBefore(start) && now.isBefore(end);
    }
}
