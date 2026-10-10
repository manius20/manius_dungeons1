package pl.s16.dungeons;

import java.time.Instant;

/** A zero deadline in a legacy recovery file never authorizes automatic deletion. */
public final class TestDeadlinePolicy {
    private TestDeadlinePolicy() {}
    public static boolean expired(Instant now,long endEpochMillis) {
        return endEpochMillis > 0L && !now.isBefore(Instant.ofEpochMilli(endEpochMillis));
    }
}
